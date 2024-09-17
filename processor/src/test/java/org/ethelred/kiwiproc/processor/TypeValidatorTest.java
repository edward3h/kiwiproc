package org.ethelred.kiwiproc.processor;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static org.ethelred.kiwiproc.processor.TestUtils.ofClass;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.karuslabs.utilitary.Logger;
import java.lang.annotation.Annotation;
import java.sql.JDBCType;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import javax.annotation.processing.Messager;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import org.ethelred.kiwiproc.meta.ArrayComponent;
import org.ethelred.kiwiproc.meta.ColumnMetaData;
import org.ethelred.kiwiproc.processor.types.*;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class TypeValidatorTest {
    TypeValidator validator = new TypeValidator(mockLogger(), mockMethodElement(), new CoreTypes());
    Set<String> messages = new HashSet<>();
    static int colCount = 1;

    @AfterEach
    void reset() {
        colCount = 1;
    }

    @ParameterizedTest
    @MethodSource
    void testQueryParameter(
            ColumnMetaData columnMetaData,
            MethodParameterInfo parameterInfo,
            boolean expectedResult,
            @Nullable String message) {
        var result = validator.validateParameters(Map.of(columnMetaData, parameterInfo), QueryMethodKind.QUERY);
        assertWithMessage("testQueryParameter %s -> %s", parameterInfo.type(), logKiwiType(columnMetaData))
                .that(result)
                .isEqualTo(expectedResult);
        if (message == null) {
            assertThat(messages).isEmpty();
        } else {
            assertThat(messages).contains(message);
        }
    }

    public static Stream<Arguments> testQueryParameter() {
        return Stream.of(
                arguments(
                        col(false, JDBCType.INTEGER),
                        new MethodParameterInfo(mockVariableElement(), "x", ofClass(int.class), false, null),
                        true,
                        null),
                arguments(
                        col(true, JDBCType.INTEGER),
                        new MethodParameterInfo(mockVariableElement(), "x", ofClass(int.class), false, null),
                        true,
                        null),
                arguments(
                        col(false, JDBCType.INTEGER),
                        new MethodParameterInfo(mockVariableElement(), "x", ofClass(Integer.class, true), false, null),
                        false,
                        "Parameter type int/nullable is not compatible with SQL type int/non-null for parameter null"),
                arguments(
                        col(true, JDBCType.INTEGER),
                        new MethodParameterInfo(mockVariableElement(), "x", ofClass(Integer.class, true), false, null),
                        true,
                        null),
                arguments(
                        col(true, JDBCType.ARRAY, new ArrayComponent(JDBCType.INTEGER, "ignored")),
                        new MethodParameterInfo(
                                mockVariableElement(),
                                "x",
                                new ContainerType(ValidContainerType.LIST, ofClass(Integer.class, true)),
                                false,
                                null),
                        true,
                        null));
    }

    @ParameterizedTest
    @MethodSource
    void testQueryReturn(
            List<ColumnMetaData> columnMetaData,
            KiwiType returnType,
            boolean expectedResult,
            @Nullable String message) {
        var result = validator.validateReturn(columnMetaData, returnType, QueryMethodKind.QUERY);
        assertWithMessage("testQueryReturn %s -> %s", logKiwiType(columnMetaData), returnType)
                .that(result)
                .isEqualTo(expectedResult);
        if (message == null) {
            assertThat(messages).isEmpty();
        } else {
            assertThat(messages).contains(message);
        }
    }

    private KiwiType logKiwiType(List<ColumnMetaData> columnMetaData) {
        if (columnMetaData.isEmpty()) {
            return KiwiType.unsupported();
        }
        return logKiwiType(columnMetaData.get(0));
    }

    private KiwiType logKiwiType(ColumnMetaData columnMetaData) {
        return SqlTypeMapping.get(columnMetaData).kiwiType();
    }

    public static Stream<Arguments> testQueryReturn() {
        return Stream.of(
                testCase(ofClass(int.class), true, null, col(false, JDBCType.INTEGER)),
                testCase(
                        new ContainerType(ValidContainerType.LIST, ofClass(int.class)),
                        true,
                        null,
                        col(false, JDBCType.INTEGER)),
                testCase(
                        new ContainerType(
                                ValidContainerType.LIST, recordType("TestRecord", "test1", ofClass(int.class))),
                        true,
                        null,
                        col(false, JDBCType.INTEGER)),
                testCase(
                        new ContainerType(
                                ValidContainerType.LIST,
                                recordType("TestRecord", "test1", ofClass(int.class), "test2", ofClass(String.class))),
                        false,
                        "Missing or incompatible column type null for component test2 type String/non-null",
                        col(false, JDBCType.INTEGER)),
                testCase(
                        new ContainerType(
                                ValidContainerType.LIST, recordType("TestRecord", "test1", ofClass(int.class))),
                        false,
                        "Missing component type for column test2 type String/non-null",
                        col(false, JDBCType.INTEGER),
                        col(false, JDBCType.VARCHAR)),
                testCase(
                        new ContainerType(
                                ValidContainerType.LIST,
                                recordType(
                                        "TestRecord",
                                        "test1",
                                        ofClass(String.class),
                                        "test2",
                                        new ContainerType(ValidContainerType.LIST, ofClass(String.class)))),
                        true,
                        null,
                        col(false, JDBCType.VARCHAR),
                        col(false, JDBCType.ARRAY, new ArrayComponent(JDBCType.VARCHAR, "ignored"))));
    }

    private static KiwiType recordType(String className, String componentName, KiwiType componentType) {
        return new RecordType("test", className, List.of(new RecordTypeComponent(componentName, componentType)));
    }

    private static KiwiType recordType(
            String className,
            String componentName,
            KiwiType componentType,
            String componentName2,
            KiwiType componentType2) {
        return new RecordType(
                "test",
                className,
                List.of(
                        new RecordTypeComponent(componentName, componentType),
                        new RecordTypeComponent(componentName2, componentType2)));
    }

    static Arguments testCase(
            KiwiType returnType, boolean expectedResult, @Nullable String message, ColumnMetaData... columns) {
        var result = arguments(List.of(columns), returnType, expectedResult, message);
        colCount = 1;
        return result;
    }

    static ColumnMetaData col(boolean nullable, JDBCType type, @Nullable ArrayComponent componentType) {
        return new ColumnMetaData(colCount, "test" + colCount++, nullable, type, componentType);
    }

    static ColumnMetaData col(boolean nullable, JDBCType type) {
        return col(nullable, type, null);
    }

    private Element mockMethodElement() {
        return new Element() {
            @Override
            public TypeMirror asType() {
                return null;
            }

            @Override
            public ElementKind getKind() {
                return null;
            }

            @Override
            public Set<Modifier> getModifiers() {
                return Set.of();
            }

            @Override
            public Name getSimpleName() {
                return null;
            }

            @Override
            public Element getEnclosingElement() {
                return null;
            }

            @Override
            public List<? extends Element> getEnclosedElements() {
                return List.of();
            }

            @Override
            public List<? extends AnnotationMirror> getAnnotationMirrors() {
                return List.of();
            }

            @Override
            public <A extends Annotation> A getAnnotation(Class<A> annotationType) {
                return null;
            }

            @Override
            public <A extends Annotation> A[] getAnnotationsByType(Class<A> annotationType) {
                return null;
            }

            @Override
            public <R, P> R accept(ElementVisitor<R, P> v, P p) {
                return null;
            }
        };
    }

    private static VariableElement mockVariableElement() {
        return new VariableElement() {
            @Override
            public TypeMirror asType() {
                return null;
            }

            @Override
            public Object getConstantValue() {
                return null;
            }

            @Override
            public ElementKind getKind() {
                return null;
            }

            @Override
            public Set<Modifier> getModifiers() {
                return Set.of();
            }

            @Override
            public Name getSimpleName() {
                return null;
            }

            @Override
            public Element getEnclosingElement() {
                return null;
            }

            @Override
            public List<? extends Element> getEnclosedElements() {
                return List.of();
            }

            @Override
            public List<? extends AnnotationMirror> getAnnotationMirrors() {
                return List.of();
            }

            @Override
            public <A extends Annotation> A getAnnotation(Class<A> annotationType) {
                return null;
            }

            @Override
            public <A extends Annotation> A[] getAnnotationsByType(Class<A> annotationType) {
                return null;
            }

            @Override
            public <R, P> R accept(ElementVisitor<R, P> v, P p) {
                return null;
            }
        };
    }

    private Logger mockLogger() {
        return new Logger(mockMessager());
    }

    private Messager mockMessager() {
        return new Messager() {
            @Override
            public void printMessage(Diagnostic.Kind kind, CharSequence msg) {
                System.out.println(msg);
                if (kind != Diagnostic.Kind.NOTE) {
                    messages.add(msg.toString());
                }
            }

            @Override
            public void printMessage(Diagnostic.Kind kind, CharSequence msg, Element e) {
                printMessage(kind, msg);
            }

            @Override
            public void printMessage(Diagnostic.Kind kind, CharSequence msg, Element e, AnnotationMirror a) {
                printMessage(kind, msg);
            }

            @Override
            public void printMessage(
                    Diagnostic.Kind kind, CharSequence msg, Element e, AnnotationMirror a, AnnotationValue v) {
                printMessage(kind, msg);
            }
        };
    }
}
