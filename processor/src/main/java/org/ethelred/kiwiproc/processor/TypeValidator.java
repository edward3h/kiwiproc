package org.ethelred.kiwiproc.processor;

import com.karuslabs.utilitary.Logger;
import org.ethelred.kiwiproc.meta.ColumnMetaData;

import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.*;

import javax.lang.model.util.SimpleTypeVisitor14;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

public class TypeValidator {
    public static final Set<Class<?>> BASIC_TYPES = Set.of(String.class, BigInteger.class, BigDecimal.class, LocalDate.class, Date.class);
    private final TypeUtils typeUtils;
    private final Logger logger;

    public TypeValidator(TypeUtils typeUtils, Logger logger) {
        this.typeUtils = typeUtils;
        this.logger = logger;
    }
    
    void info(String format, Object... args) {
        logger.note(null, String.format(format, args));
    }

    interface Info {
        void info(String format, Object... args);
    }

    public boolean validateParameters(Map<ColumnMetaData, MethodParameterInfo> parameterMapping) {
        var valid = new AtomicBoolean(true);
        parameterMapping.forEach(((columnMetaData, methodParameterInfo) -> {
            TypeMirror type = methodParameterInfo.type();
            if (!type.accept(new ParameterTypeValidator(), columnMetaData)) {
                valid.set(false);
                logger.error(methodParameterInfo.variableElement(), "Unsupported type for parameter '%s': %s".formatted(methodParameterInfo.name(), type));
            }
        }));

        return valid.get();
    }

    public boolean validateReturn(List<ColumnMetaData> columnMetaData, TypeMirror returnType) {
        return new ReturnTypeValidator().visit(returnType, new ReturnTypeContext(TypeLevel.Container, columnMetaData, this::info));
    }

    enum TypeLevel { Container, Component, Value}

    record ReturnTypeContext(TypeLevel level, List<ColumnMetaData> columnMetaData, Info info) {

        public boolean single() {
            return columnMetaData.size() == 1;
        }

        public ReturnTypeContext withLevel(TypeLevel level) {
            return new ReturnTypeContext(level, columnMetaData, info);
        }

        public boolean container() {
            return level == TypeLevel.Container;
        }

        public ReturnTypeContext asComponent() {
            return withLevel(TypeLevel.Component);
        }

        public boolean component() {
            return level == TypeLevel.Component;
        }

        public ReturnTypeContext asValue() {
            return withLevel(TypeLevel.Value);
        }

        public boolean hasColumn(Name simpleName) {
            info.info("hasColumn(%s) %s", simpleName, columnMetaData.stream().map(ColumnMetaData::name).toList());
            return columnMetaData.stream().anyMatch(cmd -> cmd.name().equalsIgnoreCase(simpleName.toString()));
        }
    }

    class ReturnTypeValidator extends SimpleTypeVisitor14<Boolean, ReturnTypeContext> {
        public ReturnTypeValidator() {
            super(false);
        }



        @Override
        public Boolean visitPrimitive(PrimitiveType t, ReturnTypeContext returnTypeContext) {
            info("Return type validate %s, level %s", t, returnTypeContext.level);
            return returnTypeContext.single() || returnTypeContext.level == TypeLevel.Value;
        }

        @Override
        public Boolean visitArray(ArrayType t, ReturnTypeContext returnTypeContext) {
            info("Return type validate %s, level %s", t, returnTypeContext.level);
            return returnTypeContext.container() && visit(t.getComponentType(), returnTypeContext.asComponent());
        }

        @Override
        public Boolean visitDeclared(DeclaredType t, ReturnTypeContext returnTypeContext) {
            info("Return type validate %s, level %s", t, returnTypeContext.level);
            try {
                var isBoxed = visitPrimitive(typeUtils.unboxedType(t), returnTypeContext); // succeeds if type is a boxed primitive
                info("Boxed %s", t);
                return isBoxed;
            } catch (IllegalArgumentException ignored) {
                // not a boxed type
            }
            if (BASIC_TYPES.stream().anyMatch(bt -> typeUtils.isSameType(t, typeUtils.type(bt)))) {
                info("Basic type %s", t);
                return returnTypeContext.single() || returnTypeContext.level == TypeLevel.Value;
            }
            if (Stream.of(ContainerType.values()).map(ContainerType::javaType).anyMatch(bt -> typeUtils.isSameType(typeUtils.erasure(t), typeUtils.erasure(typeUtils.type(bt))))) {
                var typeArguments = t.getTypeArguments();
                info("Container %s args %s level %s", typeUtils.erasure(t), typeArguments, returnTypeContext.level);
                return returnTypeContext.container() && typeArguments.size() == 1 && visit(typeArguments.get(0), returnTypeContext.asComponent());
            }
            if (typeUtils.isRecord(t) && !returnTypeContext.single() && (returnTypeContext.container() || returnTypeContext.component())) {
                info("Record %s", t);
                var asValue = returnTypeContext.asValue();
                return typeUtils.recordComponents(t).stream().allMatch(c -> asValue.hasColumn(c.getSimpleName()) && visit(c.asType(), asValue));
            }
            info("Unsupported return type %s", t);
            return false;
        }


    }

    class ParameterTypeValidator extends SimpleTypeVisitor14<Boolean, ColumnMetaData> {
        protected ParameterTypeValidator() {
            super(false);
        }

        @Override
        public Boolean visitPrimitive(PrimitiveType t, ColumnMetaData columnMetaData) {
            return true;
        }

        @Override
        public Boolean visitArray(ArrayType t, ColumnMetaData columnMetaData) {
            return visit(t.getComponentType(), columnMetaData);
        }

        @Override
        public Boolean visitDeclared(DeclaredType t, ColumnMetaData columnMetaData) {
            try {
                typeUtils.unboxedType(t); // succeeds if type is a boxed primitive
                return true;
            } catch (IllegalArgumentException ignored) {

            }
            if (BASIC_TYPES.stream()
                    .anyMatch(bt -> typeUtils.isSameType(t, typeUtils.type(bt)))) {
                return true;
            }
            if (typeUtils.isSubtype(t, typeUtils.type(Iterable.class))) {
                var typeArguments = t.getTypeArguments();
                return typeArguments.size() == 1 && visit(typeArguments.get(0), columnMetaData);
            }
            if (typeUtils.isRecord(t) && t.asElement() instanceof TypeElement typeElement) {
                var rc = typeElement.getRecordComponents();
                return !rc.isEmpty()
                        && rc.stream().allMatch(recordComponentElement -> visit(recordComponentElement.asType(), columnMetaData));
            }
            return false;
        }
    }
}
