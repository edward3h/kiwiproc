package org.ethelred.kiwiproc.processor;

import com.karuslabs.utilitary.Logger;
import java.util.*;
import javax.lang.model.element.Element;
import org.ethelred.kiwiproc.meta.ColumnMetaData;
import org.ethelred.kiwiproc.processor.types.*;

public record TypeValidator(Logger logger, Element element, CoreTypes coreTypes) {

    private static final KiwiType UPDATE_RETURN_TYPE = new PrimitiveKiwiType("int", false);
    private static final KiwiType BATCH_RETURN_TYPE = new ContainerType(ValidContainerType.ARRAY, UPDATE_RETURN_TYPE);

    public TypeValidator {
        element = Objects.requireNonNull(element);
    }

    public TypeValidator(Logger logger, Element methodElement) {
        this(logger, methodElement, new CoreTypes());
    }

    public boolean validateParameters(Map<ColumnMetaData, MethodParameterInfo> parameterMapping, QueryMethodKind kind) {
        boolean result = true;
        for (var entry : parameterMapping.entrySet()) {
            var columnMetaData = entry.getKey();
            var methodParameterInfo = entry.getValue();
            var parameterType = methodParameterInfo.type();
            if (kind == QueryMethodKind.BATCH && parameterType instanceof ContainerType containerType) {
                // unwrap container because it will be iterated for the batch
                parameterType = containerType.containedType();
            }
            var element = methodParameterInfo.variableElement();
            KiwiType columnType = SqlTypeMapping.get(columnMetaData).kiwiType();
            if (!withElement(element).validateSingleParameter(parameterType, columnType)) {
                result = false;
            }
        }
        if (kind == QueryMethodKind.BATCH
                && parameterMapping.values().stream()
                        .map(MethodParameterInfo::type)
                        .anyMatch(t -> t instanceof ContainerType)) {
            result = false;
            logger.error(element, "SqlBatch method must have at least one iterable parameter");
        }

        return result;
    }

    private TypeValidator withElement(Element element) {
        return new TypeValidator(logger, element, coreTypes);
    }

    private boolean validateSingleParameter(KiwiType parameterType, KiwiType columnType) {
        if (!validateGeneral(parameterType)) {
            logger.error(element, "Unsupported type %s for parameter %s".formatted(parameterType, simpleName()));
            return false;
        }
        if (!validateCompatible(parameterType, columnType)) {
            logger.error(
                    element,
                    "Parameter type %s is not compatible with SQL type %s for parameter %s"
                            .formatted(parameterType, columnType, simpleName()));
            return false;
        }
        return true;
    }

    private CharSequence simpleName() {
        return element == null ? "unknown element" : element.getSimpleName();
    }

    /**
     * Check whether we know how to convert "source" to "target".
     * This operation is not necessarily commutative.
     * @param source
     * @param target
     * @return
     */
    private boolean validateCompatible(KiwiType source, KiwiType target) {
        if (source.equals(target)) {
            // shortcut
            return true;
        }
        debug("Comparing %s with %s".formatted(source, target));
        /*
        We assume that a container may not contain a null. However, for adding to a target container, pretend that the
        contained type is nullable. We will skip nulls.
         */
        if (source instanceof ContainerType fromContainer && target instanceof ContainerType toContainer) {
            // we can convert any container into a different one, since they are all effectively equivalent to Iterable.
            return validateCompatible(fromContainer.containedType(), toContainer.containedType());
        }
        if (target instanceof ContainerType toContainer) {
            // we can convert a single value to a container by wrapping
            return validateCompatible(source, toContainer.containedType());
        }
        if (source instanceof ContainerType containerType
                && containerType.type() == ValidContainerType.OPTIONAL
                && target instanceof BasicType basicType) {
            // an Optional can be converted to a nullable simple type
            // TODO how to interact with Record?
            return basicType.isNullable() && validateCompatible(containerType.containedType(), basicType);
        }
        if (source instanceof RecordType fromRecord && target instanceof RecordType toRecord) {
            // Component names must match, and types must be compatible. Order is not relevant in this context.
            var toComponents = toRecord.components();
            return fromRecord.components().stream().allMatch(e -> {
                var toComponentType = toComponents.stream()
                        .filter(toComponent -> e.name().equals(toComponent.name()))
                        .findFirst()
                        .orElse(null);
                return toComponentType != null && validateCompatible(e.type(), toComponentType.type());
            });
        }
        if (source instanceof BasicType fromType
                && !fromType.isNullable()
                && target instanceof BasicType toType
                && toType.isNullable()) {
            // non-null can be converted to nullable
            return validateCompatible(fromType.withIsNullable(true), toType);
        }
        if (source instanceof BasicType fromType && target instanceof BasicType toType) {
            return validateSimpleCompatible(fromType, toType);
        }
        return false;
    }

    private boolean validateSimpleCompatible(BasicType fromType, BasicType toType) {
        if (fromType.equals(toType)) {
            // shortcut
            return true;
        }
        if (fromType.isNullable() != toType.isNullable()) {
            // nullability must match. (See validateCompatible for non-null -> null)
            return false;
        }
        if (toType.equals(CoreTypes.STRING_TYPE.withIsNullable(toType.isNullable()))) {
            // anything can be converted to String
            return true;
        }
        var typeLookup = coreTypes.lookup(fromType, toType);
        if (typeLookup.hasWarning()) {
            logger.warn(element, typeLookup.warning());
        }
        return typeLookup.isValid();
        // TODO user defined mappings
    }

    /**
     * Common check for the expected hierarchy.
     * @param type
     * @return
     */
    private boolean validateGeneral(KiwiType type) {
        // switch record pattern not available in Java 17 :-(
        if (type instanceof BasicType || type instanceof VoidType) {
            return true;
        }
        if (type instanceof ContainerType ct) {
            var contained = ct.containedType();
            return ((contained instanceof RecordType) || (contained instanceof BasicType))
                    && validateGeneral(contained);
        }
        if (type instanceof RecordType rt) {
            var componentTypes = rt.components();
            return componentTypes.stream().allMatch(t -> t.type() instanceof BasicType);
        }
        return false;
    }

    private boolean reportError(String message) {
        logger.error(element, message);
        return false;
    }

    private void info(String message) {
        logger.note(element, message);
    }

    private void debug(String message) {
        info("DEBUG: " + message);
    }

    public boolean validateReturn(List<ColumnMetaData> columnMetaData, KiwiType returnType, QueryMethodKind kind) {
        if (returnType instanceof VoidType && kind != QueryMethodKind.QUERY) {
            return true;
        }
        if (kind == QueryMethodKind.UPDATE) {
            return validateCompatible(UPDATE_RETURN_TYPE, returnType)
                    || reportError("SqlUpdate return type must be compatible with int");
        }
        if (kind == QueryMethodKind.BATCH) {
            return validateCompatible(BATCH_RETURN_TYPE, returnType)
                    || reportError("SqlBatch return type must be compatible with int[]");
        }
        // below clauses apply to kind QUERY
        if (returnType instanceof ContainerType containerType) {
            // any container is acceptable
            debug("Return type Container %s.%s".formatted(containerType.packageName(), containerType.className()));
            return validateReturn(columnMetaData, containerType.containedType(), kind);
        }
        if (columnMetaData.size() == 1 && returnType instanceof BasicType basicType) {
            debug("Return type simple %s.%s".formatted(basicType.packageName(), basicType.className()));
            // a single column result maps to a simple type
            var first = columnMetaData.get(0);
            KiwiType columnType = SqlTypeMapping.get(first).kiwiType();
            return validateCompatible(columnType, basicType);
        }
        if (returnType instanceof RecordType recordType) {
            debug("Return type record %s.%s".formatted(recordType.packageName(), recordType.className()));
            var components = recordType.components();
            return columnMetaData.stream().allMatch(cmd -> {
                        var componentType = components.stream()
                                .filter(toComponent -> cmd.name().equals(toComponent.name()))
                                .findFirst()
                                .orElse(null);
                        KiwiType columnType = SqlTypeMapping.get(cmd).kiwiType();
                        return (componentType != null
                                        && componentType.type() instanceof BasicType basicType
                                        && validateCompatible(columnType, basicType))
                                || reportError("Missing or incompatible component type %s for column %s type %s"
                                        .formatted(componentType, cmd.name(), columnType));
                    })
                    && components.stream().allMatch(cmp -> {
                        var cmd = columnMetaData.stream()
                                .filter(col -> col.name().equals(cmp.name()))
                                .findFirst()
                                .orElse(null);
                        var colType =
                                cmd == null ? null : SqlTypeMapping.get(cmd).kiwiType();
                        return (cmd != null && validateCompatible(colType, cmp.type()))
                                || reportError("Missing or incompatible column type %s for component %s type %s"
                                        .formatted(colType, cmp.name(), cmp.type()));
                    });
        }

        return false;
    }
}
