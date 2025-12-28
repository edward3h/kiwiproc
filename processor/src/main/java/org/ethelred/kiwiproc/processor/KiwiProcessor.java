/* (C) Edward Harman 2024 */
package org.ethelred.kiwiproc.processor;

import static org.ethelred.kiwiproc.processor.QueryMethodKind.*;

import com.karuslabs.utilitary.AnnotationProcessor;
import io.avaje.jsonb.JsonType;
import io.avaje.jsonb.Jsonb;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import org.ethelred.kiwiproc.annotation.DAO;
import org.ethelred.kiwiproc.meta.*;
import org.ethelred.kiwiproc.processor.generator.PoetDAOGenerator;
import org.ethelred.kiwiproc.processor.types.TypeUtils;
import org.ethelred.kiwiproc.processorconfig.DataSourceConfig;
import org.ethelred.kiwiproc.processorconfig.DataSourceConfigJsonAdapter;
import org.ethelred.kiwiproc.processorconfig.ProcessorConfig;
import org.ethelred.kiwiproc.processorconfig.ProcessorConfigJsonAdapter;
import org.jspecify.annotations.Nullable;

@SupportedAnnotationTypes({
    "org.ethelred.kiwiproc.annotation.DAO",
    "org.ethelred.kiwiproc.annotation.ResultQuery",
    "org.ethelred.kiwiproc.annotation.UpdateQuery"
})
@SupportedSourceVersion(SourceVersion.RELEASE_17)
@SupportedOptions({KiwiProcessor.CONFIGURATION_OPTION})
public class KiwiProcessor extends AnnotationProcessor {
    public static final String CONFIGURATION_OPTION = "org.ethelred.kiwiproc.configuration";

    private ProcessorConfig config = ProcessorConfig.EMPTY;
    private @Nullable TypeUtils typeUtils;
    private final Map<String, DatabaseWrapper> databases = new HashMap<>();
    private @Nullable DAOGenerator codeGenerator;
    private CoreTypes coreTypes = new CoreTypes();

    // automated adapter discovery doesn't work in annotation processor
    Jsonb jsonb = Jsonb.builder()
            .add(ProcessorConfig.class, ProcessorConfigJsonAdapter::new)
            .add(DataSourceConfig.class, DataSourceConfigJsonAdapter::new)
            .build();
    JsonType<ProcessorConfig> configType = jsonb.type(ProcessorConfig.class);

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        var configPath = processingEnv.getOptions().get(CONFIGURATION_OPTION);
        config = loadConfig(configPath);
        typeUtils = new TypeUtils(elements, types, logger);
        codeGenerator =
                new PoetDAOGenerator(logger, processingEnv.getFiler(), config.dependencyInjectionStyle(), coreTypes);
    }

    private ProcessorConfig loadConfig(@Nullable String configPath) {
        if (configPath == null || configPath.isBlank()) {
            logger.error(null, "No config file specified.");
        } else {
            var path = Path.of(configPath);
            if (!Files.exists(path)) {
                logger.error(null, "Config file '%s' not found.".formatted(path));
            } else {
                try (var reader = Files.newBufferedReader(path)) {
                    var config = configType.fromJson(reader);
                    if (config.dataSources().isEmpty()) {
                        logger.error(null, "No datasources in config file '%s'.".formatted(path));
                    } else {
                        return config;
                    }
                } catch (Exception e) {
                    logger.error(
                            null,
                            "Exception reading config file '%s'. %s%n%s"
                                    .formatted(path, e.getMessage(), stackTrace(e)));
                }
            }
        }
        return ProcessorConfig.EMPTY;
    }

    private String stackTrace(Exception e) {
        var stringWriter = new StringWriter();
        e.printStackTrace(new PrintWriter(stringWriter));
        return stringWriter.toString();
    }

    private DatabaseWrapper getDatabase(String name) {
        return databases.computeIfAbsent(
                name, n -> new DatabaseWrapper(n, config.dataSources().getOrDefault(n, null)));
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        try {
            for (var element : roundEnv.getElementsAnnotatedWith(DAO.class)) {
                if (element.getKind() == ElementKind.INTERFACE) {
                    processInterface((TypeElement) element);
                } else {
                    logger.error(element, "@DAO is only permitted on interfaces");
                }
            }
        } catch (SQLException e) {
            logger.error(null, "Exception while processing. " + e);
        }
        return true;
    }

    @Nullable private DAOMethodInfo processMethod(
            QueryMethodKind kind, DatabaseWrapper databaseWrapper, ExecutableElement methodElement) {
        var parsedSql = ParsedQuery.parse(kind.getSql(methodElement));
        QueryMetaData queryMetaData;
        try {
            queryMetaData = databaseWrapper.getQueryMetaData(parsedSql.parsedSql());
        } catch (SQLException e) {
            logger.error(methodElement, "\n" + e.getMessage());
            return null;
        }
        var parameterInfo = MethodParameterInfo.fromElements(
                Objects.requireNonNull(typeUtils), methodElement.getParameters(), kind);
        Map<ColumnMetaData, MethodParameterInfo> parameterMapping =
                mapParameters(methodElement, parsedSql.parameterNames(), queryMetaData.parameters(), parameterInfo);
        if (kind == BATCH) {
            System.err.println("processMethod parameterInfo "
                    + parameterInfo.stream()
                            .map(Objects::toString)
                            .collect(Collectors.joining("\n    ", "\n    ", "")));
            parameterMapping.forEach(
                    (col, mpi) -> System.err.println("col " + col.index() + " parameter " + mpi.name()));
        }
        var typeValidator = new TypeValidator(logger, methodElement, coreTypes, config.debug());
        if (!typeValidator.validateParameters(parameterMapping, kind)) {
            return null;
        }
        List<DAOParameterInfo> templateParameterMapping = DAOParameterInfo.from(coreTypes, typeUtils, parameterMapping);
        var returnType = typeUtils.kiwiType(methodElement.getReturnType());
        var resultContext = new QueryResultContext(
                kind,
                queryMetaData.resultColumns(),
                kind.getKeyColumn(methodElement),
                kind.getValueColumn(methodElement));
        var unusedColumns = new HashSet<>(queryMetaData.resultColumns());
        DAOResultMapping columnMapping = typeValidator.validateReturn(resultContext, returnType, unusedColumns::remove);
        if (!columnMapping.isValid()) {
            logger.error(methodElement, "Invalid return type");
            return null;
        }
        if (!unusedColumns.isEmpty()) {
            logger.error(
                    methodElement,
                    "Unused columns in the result: "
                            + unusedColumns.stream()
                                    .map(ColumnMetaData::name)
                                    .map(SqlName::toString)
                                    .collect(Collectors.joining(", ")));
            return null;
        }
        //        List<DAOResultColumn> multipleColumnResults = new ArrayList<>();
        //        DAOResultColumn singleColumnResult = null;
        //        var returnComponentType =
        //                returnType instanceof CollectionType containerType ? containerType.containedType() :
        // returnType;
        //        returnComponentType = returnComponentType instanceof OptionalType optionalType
        //                ? optionalType.containedType()
        //                : returnComponentType;
        //        if (kind == QUERY && queryMetaData.resultColumns().size() > 1) {
        //            if (returnComponentType instanceof RecordType recordType) {
        //                recordType.components().forEach((component) -> {
        //                    var colOpt = queryMetaData.resultColumns().stream()
        //                            .filter(c -> component.name().equivalent(c.name()))
        //                            .findFirst();
        //                    colOpt.ifPresentOrElse(
        //                            col -> multipleColumnResults.add(
        //                                    new DAOResultColumn(col.name(), SqlTypeMappingRegistry.get(col),
        // component.type())),
        //                            () -> logger.error(
        //                                    methodElement,
        //                                    "No matching column found for record '%s' component '%s'"
        //                                            .formatted(recordType.className(), component.name())));
        //                });
        //            } else {
        //                logger.error(methodElement, "A query with multiple columns must be mapped to a Record type");
        //            }
        //        } else if (queryMetaData.resultColumns().size() == 1) {
        //            var col = queryMetaData.resultColumns().get(0);
        //            singleColumnResult = new DAOResultColumn(col.name(), SqlTypeMappingRegistry.get(col),
        // returnComponentType);
        //        }
        List<DAOBatchIterator> batchIterators = List.of();
        if (kind == BATCH) {
            batchIterators = parameterMapping.entrySet().stream()
                    .map(DAOBatchIterator::from)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .distinct()
                    .toList();
        }
        return new DAOMethodInfo(
                methodElement,
                Signature.fromMethod(returnType, methodElement),
                kind,
                parsedSql,
                templateParameterMapping,
                columnMapping.getColumns(),
                batchIterators);
    }

    private Map<ColumnMetaData, MethodParameterInfo> mapParameters(
            ExecutableElement methodElement,
            List<String> parameterNames,
            List<ColumnMetaData> queryParameters,
            Set<MethodParameterInfo> methodParameters) {
        Map<ColumnMetaData, MethodParameterInfo> r = new HashMap<>();
        for (var queryParameter : queryParameters) {
            var name = parameterNames.get(queryParameter.index() - 1);
            var methodParameter = methodParameters.stream()
                    .filter(mp -> mp.name().equivalent(new SqlName(name)))
                    .findFirst()
                    .orElse(null);
            if (methodParameter == null) {
                logger.error(methodElement, "No method parameter found for query parameter '%s'".formatted(name));
            } else {
                r.put(queryParameter, methodParameter);
            }
        }
        return r;
    }

    private void processInterface(TypeElement interfaceElement) throws SQLException {
        Objects.requireNonNull(typeUtils, "processInterface called before init?");
        Objects.requireNonNull(codeGenerator, "processInterface called before init?");
        var daoAnn = DAOPrism.getInstanceOn(interfaceElement);
        var dataSourceName = daoAnn.dataSourceName();
        var databaseWrapper = getDatabase(dataSourceName);
        if (!databaseWrapper.isValid()) {
            logger.error(
                    interfaceElement,
                    "Could not get valid datasource for methodName '%s'. %s"
                            .formatted(
                                    dataSourceName, databaseWrapper.getError().getMessage()));
            return;
        }
        DAOClassInfoBuilder.ElementStage elementStage = DAOClassInfoBuilder.builder();
        String daoName = interfaceElement.getSimpleName().toString();
        String packageName = typeUtils.packageName(interfaceElement);
        DAOClassInfoBuilder builderStage = elementStage
                .element(interfaceElement)
                .annotation(daoAnn)
                .packageName(packageName)
                .daoName(daoName)
                .builder();
        for (var methodElement : ElementFilter.methodsIn(Set.copyOf(interfaceElement.getEnclosedElements()))) {
            var kinds = QueryMethodKind.forMethod(methodElement);
            if (kinds.isEmpty()) {
                logger.error(
                        methodElement,
                        "Must have a '@SqlQuery', '@SqlUpdate' or '@SqlBatch' annotation or a 'default' implementation.");
            } else if (kinds.size() > 1) {
                logger.error(methodElement, "May only have one Sql annotation, or be default.");
            }
            var kind = kinds.iterator().next(); // get first element
            if (kind == DEFAULT) {
                continue;
            }

            DAOMethodInfo methodInfo = processMethod(kind, databaseWrapper, methodElement);
            if (methodInfo != null) {
                builderStage.addMethods(methodInfo);
            }
        }
        if (builderStage.methods().isEmpty()) {
            logger.error(interfaceElement, "No valid Sql or default methods found.");
            return;
        }
        var classInfo = builderStage.build();
        codeGenerator.generateImplementations(classInfo);
    }
}
