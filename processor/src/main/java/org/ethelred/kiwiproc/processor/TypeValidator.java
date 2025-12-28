/* (C) Edward Harman 2024 */
package org.ethelred.kiwiproc.processor;

import static org.ethelred.kiwiproc.processor.DAOResultMapping.INVALID;

import com.karuslabs.utilitary.Logger;
import java.util.*;
import java.util.function.Consumer;
import javax.lang.model.element.Element;
import org.ethelred.kiwiproc.meta.ColumnMetaData;
import org.ethelred.kiwiproc.processor.types.*;

public record TypeValidator(Logger logger, Element element, CoreTypes coreTypes, boolean debug) {

    public boolean validateParameters(Map<ColumnMetaData, MethodParameterInfo> parameterMapping, QueryMethodKind kind) {
        boolean result = true;
        for (var entry : parameterMapping.entrySet()) {
            var columnMetaData = entry.getKey();
            var methodParameterInfo = entry.getValue();
            var parameterType = methodParameterInfo.type();
            if (kind == QueryMethodKind.BATCH && parameterType instanceof CollectionType collectionType) {
                // unwrap container because it will be iterated for the batch
                parameterType = collectionType.containedType();
            }
            var element = methodParameterInfo.variableElement();
            KiwiType columnType = SqlTypeMappingRegistry.get(columnMetaData).kiwiType();
            if (!withElement(element).validateSingleParameter(parameterType, columnType)) {
                result = false;
            }
        }
        if (kind == QueryMethodKind.BATCH
                && parameterMapping.values().stream().noneMatch(MethodParameterInfo::batchIterate)) {
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
        Conversion conversion = validateCompatible(parameterType, columnType);
        if (!conversion.isValid()) {
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
    private Conversion validateCompatible(KiwiType source, KiwiType target) {
        debug("Comparing %s with %s".formatted(source, target));
        return coreTypes.lookup(source, target);
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
        if (type instanceof CollectionType ct) {
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

    private DAOResultMapping reportError(String message) {
        logger.error(element, message);
        return INVALID;
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

    public DAOResultMapping validateReturn(
            QueryResultContext context, KiwiType returnType, Consumer<ColumnMetaData> mappedColumn) {
        //        debug("validateReturn %s, %s, %s".formatted(columnMetaDataList, returnType, kind));
        var kind = context.kind();
        if (returnType instanceof VoidType && kind != QueryMethodKind.QUERY) {
            return new DAOResultMapping(new VoidConversion());
        } else if (returnType instanceof VoidType) {
            return reportError("Why would you want to return void from a query?");
        }
        if (kind == QueryMethodKind.UPDATE) {
            var conversion = validateCompatible(CoreTypes.UPDATE_RETURN_TYPE, returnType);
            if (!conversion.isValid()) {
                return reportError(
                        "Return type %s is invalid for SqlUpdate. Must be compatible with int".formatted(returnType));
            }
            return new DAOResultMapping(conversion);
        }
        if (kind == QueryMethodKind.BATCH) {
            var conversion = validateCompatible(CoreTypes.BATCH_RETURN_TYPE, returnType);
            if (!conversion.isValid()) {
                return reportError(
                        "Return type %s is invalid for SqlBatch. Must be compatible with int[]".formatted(returnType));
            }
            return new DAOResultMapping(conversion);
        }
        // below clauses apply to kind QUERY
        if (returnType instanceof CollectionType collectionType) {
            if (collectionType.isSimple() && context.resultPart() != ResultPart.KEY) {
                var maybeColumn = context.getSingleMatchingColumn();
                if (maybeColumn.isPresent()) {
                    ColumnMetaData columnMetaData = maybeColumn.get();
                    SqlTypeMapping sqlTypeMapping = SqlTypeMappingRegistry.get(columnMetaData);
                    var columnType = sqlTypeMapping.kiwiType();
                    if (columnType instanceof SqlArrayType) {
                        var conversion = validateCompatible(columnType, collectionType);
                        if (conversion.isValid()) {
                            var result = new DAOResultMapping();
                            mappedColumn.accept(columnMetaData);
                            result.addColumn(new DAOResultColumn(
                                    columnMetaData.name(),
                                    sqlTypeMapping,
                                    collectionType,
                                    context.resultPart(),
                                    conversion));
                            return result;
                        }
                    }
                }
            }
            // any container is acceptable
            debug("Return type Container %s.%s".formatted(collectionType.packageName(), collectionType.className()));
            return validateReturn(context.withAsParameter(true), collectionType.containedType(), mappedColumn);
        }
        if (returnType instanceof MapType mapType) {
            debug("Return type %s".formatted(mapType));
            System.err.println("map type columns" + context.columns());
            var keyMapping = validateReturn(
                    context.withMapMapping(ResultPart.KEY).withAsParameter(true),
                    mapType.keyType().withIsNullable(false),
                    mappedColumn);
            var valueMapping = validateReturn(
                    context.withMapMapping(ResultPart.VALUE).withAsParameter(true),
                    mapType.valueType().withIsNullable(false),
                    mappedColumn);
            return keyMapping.merge(valueMapping);
        }
        if (returnType.isSimple()) {
            debug("Return type simple %s.%s".formatted(returnType.packageName(), returnType.className()));
            // a single column result maps to a simple type
            var columnMetaData = context.getSingleMatchingColumn();
            if (columnMetaData.isPresent()) {
                ColumnMetaData t = columnMetaData.get();
                SqlTypeMapping sqlTypeMapping = SqlTypeMappingRegistry.get(t);
                KiwiType columnType = sqlTypeMapping.kiwiType();
                mappedColumn.accept(t);
                KiwiType target = context.asParameter() ? returnType.withIsNullable(false) : returnType;
                Conversion conversion = validateCompatible(columnType, target);
                return new DAOResultMapping(
                        new DAOResultColumn(t.name(), sqlTypeMapping, target, context.resultPart(), conversion));
            }
            return INVALID;
        }
        if (returnType instanceof RecordType recordType) {
            debug("Return type record %s.%s".formatted(recordType.packageName(), recordType.className()));
            var components = recordType.components();
            try {
                return components.stream()
                        .map(component -> {
                            ColumnMetaData columnMetaData = getColumnMetaData(context, recordType, component);
                            SqlTypeMapping sqlTypeMapping = SqlTypeMappingRegistry.get(columnMetaData);
                            var columnType = sqlTypeMapping.kiwiType();
                            mappedColumn.accept(columnMetaData);
                            var conversion = validateCompatible(columnType, component.type());
                            if (!conversion.isValid()) {
                                reportError("Missing or incompatible column type %s for component %s type %s"
                                        .formatted(columnType, component.name(), component.type()));
                            }
                            return new DAOResultColumn(
                                    columnMetaData.name(),
                                    sqlTypeMapping,
                                    component.type(),
                                    context.resultPart(),
                                    conversion);
                        })
                        .collect(DAOResultMapping::new, DAOResultMapping::addColumn, DAOResultMapping::merge);
            } catch (IllegalArgumentException e) {
                // already reported above, just return
                return INVALID;
            }
        }
        if (returnType instanceof OptionalType optionalType) {
            debug("Return type Optional %s.%s".formatted(optionalType.packageName(), optionalType.className()));
            return validateReturn(context, optionalType.containedType(), mappedColumn);
        }
        debug("Unmatched return type? %s".formatted(returnType));
        return INVALID;
    }

    private ColumnMetaData getColumnMetaData(
            QueryResultContext context, RecordType recordType, RecordTypeComponent component) {
        var matchingColumn = context.getMatchingColumn(component.name());
        if (matchingColumn.isEmpty()) {
            reportError("Record component '%s.%s' does not have a matching column"
                    .formatted(recordType.className(), component.name()));
            throw new IllegalArgumentException();
        }
        return matchingColumn.get();
    }
}
