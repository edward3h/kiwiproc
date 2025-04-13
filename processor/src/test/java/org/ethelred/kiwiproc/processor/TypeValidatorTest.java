/* (C) Edward Harman 2024 */
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
import org.ethelred.kiwiproc.meta.JDBCNullable;
import org.ethelred.kiwiproc.meta.JavaName;
import org.ethelred.kiwiproc.processor.types.*;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class TypeValidatorTest {
    TypeValidator validator = new TypeValidator(mockLogger(), mockMethodElement(), new CoreTypes(), false);
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
                        new MethodParameterInfo(
                                mockVariableElement(), new JavaName("x"), ofClass(int.class), false, null),
                        true,
                        null),
                arguments(
                        col(true, JDBCType.INTEGER),
                        new MethodParameterInfo(
                                mockVariableElement(), new JavaName("x"), ofClass(int.class), false, null),
                        true,
                        null),
                arguments(
                        col(true, JDBCType.INTEGER),
                        new MethodParameterInfo(
                                mockVariableElement(), new JavaName("x"), ofClass(Integer.class, true), false, null),
                        true,
                        null),
                arguments(
                        col(true, JDBCType.ARRAY, new ArrayComponent(JDBCType.INTEGER, "ignored")),
                        new MethodParameterInfo(
                                mockVariableElement(),
                                new JavaName("x"),
                                new CollectionType(ValidCollection.LIST, ofClass(Integer.class, true)),
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
        var context = new QueryResultContext(QueryMethodKind.QUERY, columnMetaData, null, null);
        var result = validator.validateReturn(context, returnType, x -> {});
        assertWithMessage("testQueryReturn %s -> %s", logKiwiType(columnMetaData), returnType)
                .that(result.isValid())
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
        return SqlTypeMappingRegistry.get(columnMetaData).kiwiType();
    }

    public static Stream<Arguments> testQueryReturn() {
        return Stream.of(
                testCase(ofClass(int.class), true, null, col(false, JDBCType.INTEGER)),
                testCase(
                        new CollectionType(ValidCollection.LIST, ofClass(int.class)),
                        true,
                        null,
                        col(false, JDBCType.INTEGER)),
                testCase(
                        new CollectionType(ValidCollection.LIST, recordType("TestRecord", "test1", ofClass(int.class))),
                        true,
                        null,
                        col(false, JDBCType.INTEGER)),
                testCase(
                        new CollectionType(
                                ValidCollection.LIST,
                                recordType("TestRecord", "test1", ofClass(int.class), "test2", ofClass(String.class))),
                        false,
                        "Record component 'TestRecord.test2' does not have a matching column",
                        col(false, JDBCType.INTEGER)),
                //                testCase( TODO separate exhaustiveness check
                //                        new CollectionType(ValidCollection.LIST, recordType("TestRecord", "test1",
                // ofClass(int.class))),
                //                        false,
                //                        "Record 'TestRecord' does not have a component matching column 'test2'",
                //                        col(false, JDBCType.INTEGER),
                //                        col(false, JDBCType.VARCHAR)),
                testCase(
                        new CollectionType(
                                ValidCollection.LIST,
                                recordType(
                                        "TestRecord",
                                        "test1",
                                        ofClass(String.class),
                                        "test2",
                                        new CollectionType(ValidCollection.LIST, ofClass(String.class)))),
                        true,
                        null,
                        col(false, JDBCType.VARCHAR),
                        col(false, JDBCType.ARRAY, new ArrayComponent(JDBCType.VARCHAR, "ignored"))));
    }

    private static KiwiType recordType(String className, String componentName, KiwiType componentType) {
        return new RecordType(
                "test", className, false, List.of(new RecordTypeComponent(new JavaName(componentName), componentType)));
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
                false,
                List.of(
                        new RecordTypeComponent(new JavaName(componentName), componentType),
                        new RecordTypeComponent(new JavaName(componentName2), componentType2)));
    }

    static Arguments testCase(
            KiwiType returnType, boolean expectedResult, @Nullable String message, ColumnMetaData... columns) {
        var result = arguments(List.of(columns), returnType, expectedResult, message);
        colCount = 1;
        return result;
    }

    static ColumnMetaData col(boolean nullable, JDBCType type, @Nullable ArrayComponent componentType) {
        return new ColumnMetaData(
                colCount,
                false,
                "test" + colCount++,
                nullable ? JDBCNullable.NULLABLE : JDBCNullable.NOT_NULL,
                type,
                "butt",
                "poop",
                componentType);
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
