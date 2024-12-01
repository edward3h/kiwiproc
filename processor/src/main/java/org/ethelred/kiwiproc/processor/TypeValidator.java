/* (C) Edward Harman 2024 */
package org.ethelred.kiwiproc.processor;

import com.karuslabs.utilitary.Logger;
import java.util.*;
import javax.lang.model.element.Element;
import org.ethelred.kiwiproc.meta.ColumnMetaData;
import org.ethelred.kiwiproc.processor.types.*;

public record TypeValidator(Logger logger, Element element, CoreTypes coreTypes, boolean debug) {

    private static final KiwiType UPDATE_RETURN_TYPE = new PrimitiveKiwiType("int", false);
    private static final KiwiType BATCH_RETURN_TYPE = new ContainerType(ValidContainerType.ARRAY, UPDATE_RETURN_TYPE);

    public TypeValidator(Logger logger, Element methodElement, boolean debug) {
        this(logger, methodElement, new CoreTypes(), debug);
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
        return new TypeValidator(logger, element, coreTypes, debug);
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
        return element.getSimpleName();
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

        // check for a valid conversion
        // don't fail on invalid conversion because there are other possible cases
        Conversion c = coreTypes.lookup(source, target);
        if (c.isValid()) {
            if (c.hasWarning()) {
                warn(Objects.requireNonNull(c.warning()));
            }
            return true;
        }
        if (target instanceof ContainerType targetContainer) {
            // we can convert a single value to a container by wrapping
            return validateCompatible(source, targetContainer.containedType());
        }
        if (source instanceof ContainerType containerType
                && containerType.type() == ValidContainerType.OPTIONAL
                && target.isSimple()) {
            // an Optional can be converted to a nullable simple type
            // TODO how to interact with Record?
            return target.isNullable() && validateCompatible(containerType.containedType(), target);
        }
        return false;
    }

    /**
     * Common check for the expected hierarchy.
     * @param type
     * @return
     */
    private boolean validateGeneral(KiwiType type) {
        // switch record pattern not available in Java 17 :-(
        if (type.isSimple() || type instanceof VoidType) {
            return true;
        }
        if (type instanceof ContainerType ct) {
            var contained = ct.containedType();
            return ((contained instanceof RecordType) || (contained.isSimple())) && validateGeneral(contained);
        }
        if (type instanceof SqlArrayType sqlArrayType) {
            var contained = sqlArrayType.containedType();
            return contained.isSimple() && validateGeneral(contained);
        }
        if (type instanceof RecordType rt) {
            var componentTypes = rt.components();
            return componentTypes.stream().allMatch(t -> t.type().isSimple());
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
        if (debug) {
            info("DEBUG: " + message);
        }
    }

    private void warn(String message) {
        logger.warn(element, message);
    }

    public boolean validateReturn(List<ColumnMetaData> columnMetaDataList, KiwiType returnType, QueryMethodKind kind) {
        if (returnType instanceof VoidType && kind != QueryMethodKind.QUERY) {
            return true;
        } else if (returnType instanceof VoidType) {
            return reportError("Why would you want to return void from a query?");
        }
        if (kind == QueryMethodKind.UPDATE) {
            return validateCompatible(UPDATE_RETURN_TYPE, returnType)
                    || reportError("Return type %s is invalid for SqlUpdate. Must be compatible with int"
                            .formatted(returnType));
        }
        if (kind == QueryMethodKind.BATCH) {
            return validateCompatible(BATCH_RETURN_TYPE, returnType)
                    || reportError("Return type %s is invalid for SqlBatch. Must be compatible with int[]"
                            .formatted(returnType));
        }
        // below clauses apply to kind QUERY
        if (returnType instanceof ContainerType containerType) {
            // any container is acceptable
            debug("Return type Container %s.%s".formatted(containerType.packageName(), containerType.className()));
            return validateReturn(columnMetaDataList, containerType.containedType(), kind);
        }
        if (columnMetaDataList.size() == 1 && returnType.isSimple()) {
            debug("Return type simple %s.%s".formatted(returnType.packageName(), returnType.className()));
            // a single column result maps to a simple type
            var firstColumnMetaData = columnMetaDataList.get(0);
            KiwiType columnType = SqlTypeMapping.get(firstColumnMetaData).kiwiType();
            return validateCompatible(columnType, returnType);
        }
        if (returnType instanceof RecordType recordType) {
            debug("Return type record %s.%s".formatted(recordType.packageName(), recordType.className()));
            var components = recordType.components();
            return columnMetaDataList.stream().allMatch(columnMetaData -> {
                        var matchingComponent = components.stream()
                                .filter(targetComponent -> columnMetaData.name().equivalent(targetComponent.name()))
                                .findFirst()
                                .orElse(null);
                        if (matchingComponent == null) {
                            return reportError("Record '%s' does not have a component matching column '%s'"
                                    .formatted(recordType.className(), columnMetaData.name()));
                        }
                        KiwiType columnType = SqlTypeMapping.get(columnMetaData).kiwiType();
                        return validateCompatible(columnType, matchingComponent.type())
                                || reportError("Incompatible component type %s for column %s type %s"
                                        .formatted(matchingComponent, columnMetaData.name(), columnType));
                    })
                    && components.stream().allMatch(component -> {
                        var matchingColumn = columnMetaDataList.stream()
                                .filter(columnMetaData -> columnMetaData.name().equivalent(component.name()))
                                .findFirst()
                                .orElse(null);
                        if (matchingColumn == null) {
                            return reportError("Record component '%s.%s' does not have a matching column"
                                    .formatted(recordType.className(), component.name()));
                        }
                        var columnType = SqlTypeMapping.get(matchingColumn).kiwiType();
                        return validateCompatible(columnType, component.type())
                                || reportError("Missing or incompatible column type %s for component %s type %s"
                                        .formatted(columnType, component.name(), component.type()));
                    });
        }

        return false;
    }
}
