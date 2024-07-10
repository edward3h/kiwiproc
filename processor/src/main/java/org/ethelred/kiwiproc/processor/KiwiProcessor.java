package org.ethelred.kiwiproc.processor;

import com.karuslabs.utilitary.AnnotationProcessor;
import io.avaje.jsonb.JsonType;
import org.ethelred.kiwiproc.annotation.DAO;
import org.ethelred.kiwiproc.meta.ColumnMetaData;
import org.ethelred.kiwiproc.meta.DatabaseWrapper;
import org.ethelred.kiwiproc.meta.ParsedQuery;
import org.ethelred.kiwiproc.processorconfig.DataSourceConfig;
import org.ethelred.kiwiproc.processorconfig.ProcessorConfig;
import io.avaje.jsonb.Jsonb;
import org.ethelred.kiwiproc.processorconfig.jsonb.DataSourceConfigJsonAdapter;
import org.ethelred.kiwiproc.processorconfig.jsonb.ProcessorConfigJsonAdapter;
import org.jspecify.annotations.Nullable;
import org.kohsuke.MetaInfServices;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

import static org.ethelred.kiwiproc.processor.QueryMethodKind.DEFAULT;
import static org.ethelred.kiwiproc.processor.QueryMethodKind.QUERY;

@MetaInfServices(Processor.class)
@SupportedAnnotationTypes({
        "org.ethelred.kiwiproc.annotation.DAO",
        "org.ethelred.kiwiproc.annotation.ResultQuery",
        "org.ethelred.kiwiproc.annotation.UpdateQuery"
})
@SupportedSourceVersion(SourceVersion.RELEASE_17)
@SupportedOptions({
        KiwiProcessor.CONFIGURATION_OPTION
})
public class KiwiProcessor extends AnnotationProcessor implements ClassNameMixin {
    public static final String CONFIGURATION_OPTION = "org.ethelred.kiwiproc.configuration";

    private ProcessorConfig config = ProcessorConfig.EMPTY;
    private @Nullable TypeUtils typeUtils;
    private @Nullable TypeValidator typeValidator;
    private final Map<String, DatabaseWrapper> databases = new HashMap<>();
    private @Nullable DAOGenerator generator;

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
        typeValidator = new TypeValidator(typeUtils, logger);
        generator = new DAOGenerator(processingEnv.getFiler(), config.dependencyInjectionStyle());
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
                    logger.error(null, "Exception reading config file '%s'. %s%n%s".formatted(path, e.getMessage(), stackTrace(e)));
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
                name,
                n -> new DatabaseWrapper(n, config.dataSources().getOrDefault(n, null))
        );
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

    @Nullable
    private DAOMethodInfo processMethod(String daoName, QueryMethodKind kind, DatabaseWrapper databaseWrapper, ExecutableElement methodElement) throws SQLException {
        var parsedSql = ParsedQuery.parse(
            kind.getSql(methodElement)
        );
        var queryMetaData = databaseWrapper.getQueryMetaData(parsedSql.parsedSql());
        var parameterInfo = MethodParameterInfo.fromElements(typeUtils, methodElement.getParameters());
        Map<ColumnMetaData, MethodParameterInfo> parameterMapping = mapParameters(methodElement, parsedSql.parameterNames(), queryMetaData.parameters(), parameterInfo);
        if (!typeValidator.validateParameters(parameterMapping)) {
            return null;
        }
        List<DAOParameterInfo> templateParameterMapping = DAOParameterInfo.from(typeUtils, parameterMapping);
        var returnType = methodElement.getReturnType();
        if (!typeValidator.validateReturn(queryMetaData.resultColumns(), returnType) || !kind.validateReturn(typeUtils, returnType)) {
            logger.error(methodElement, "Invalid return type");
            return null;
        }
        Signature rowRecord = null;
        DAOResultInfo singleColumnResult = null;
        if (kind == QUERY && queryMetaData.resultColumns().size() > 1) {
            var rowBuilder = SignatureBuilder.builder().name(className(methodElement.getSimpleName() + "$Row", daoName));
            queryMetaData.resultColumns().forEach(col -> {
                var name = col.name();
                rowBuilder.addParams(new DAOResultInfo(name, col.javaType(), SqlTypeMapping.get(col.sqlType())));
            });
            rowRecord = rowBuilder.build();
        } else if (queryMetaData.resultColumns().size() == 1) {
            var col = queryMetaData.resultColumns().get(0);
            singleColumnResult = new DAOResultInfo(col.name(), col.javaType(), SqlTypeMapping.get(col.sqlType()));
        }
        return new DAOMethodInfo(Signature.fromMethod(typeUtils, methodElement), kind, parsedSql, templateParameterMapping, rowRecord, singleColumnResult);
    }

    private Map<ColumnMetaData, MethodParameterInfo> mapParameters(ExecutableElement methodElement, List<String> parameterNames, List<ColumnMetaData> queryParameters, Map<String, MethodParameterInfo> methodParameters) {
        Map<ColumnMetaData, MethodParameterInfo> r = new HashMap<>();
        for (var queryParameter: queryParameters) {
            var name = parameterNames.get(queryParameter.index() - 1);
            var methodParameter = methodParameters.get(name);
            if (methodParameter == null) {
                logger.error(methodElement, "No method parameter found for query parameter '%s'".formatted(queryParameter.name()));
            } else {
                r.put(queryParameter, methodParameter);
            }
        }
        return r;
    }

    private void processInterface(TypeElement interfaceElement) throws SQLException {
        var daoAnn = DAOPrism.getInstanceOn(interfaceElement);
        var dataSourceName = daoAnn.dataSourceName();
        var databaseWrapper = getDatabase(dataSourceName);
        if (!databaseWrapper.isValid()) {
            logger.error(interfaceElement, "Could not get valid datasource for name '%s'. %s".formatted(dataSourceName, databaseWrapper.getError().getMessage()));
            return;
        }
        var classBuilder = DAOClassInfoBuilder.builder();
        String daoName = interfaceElement.getSimpleName().toString();
        classBuilder.element(interfaceElement).annotation(daoAnn).daoName(daoName).packageName(typeUtils.packageName(interfaceElement));
        for (var methodElement: ElementFilter.methodsIn(Set.copyOf(interfaceElement.getEnclosedElements()))) {
            var kinds = QueryMethodKind.forMethod(methodElement);
            if (kinds.isEmpty()) {
                logger.error(methodElement, "Must have a '@SqlQuery', '@SqlUpdate' or '@SqlBatch' annotation or a 'default' implementation.");
            } else if (kinds.size() > 1) {
                logger.error(methodElement, "May only have one Sql annotation, or be default.");
            }
            var kind = kinds.iterator().next();
            if (kind == DEFAULT) {
                continue;
            }

            DAOMethodInfo methodInfo = processMethod(daoName, kinds.iterator().next(), databaseWrapper, methodElement);
            if (methodInfo != null) {
                classBuilder.addMethods(methodInfo);
            }

        }
        if (classBuilder.methods().isEmpty()) {
            logger.error(interfaceElement, "No valid Sql or default methods found.");
            return;
        }
        var classInfo = classBuilder.build();
        var mappings = classInfo.methods().stream()
                        .flatMap(DAOMethodInfo::mappers).collect(Collectors.toSet());
        generator.generateProvider(classInfo);
        generator.generateMapper(classInfo, mappings);
        generator.generateImpl(classInfo);
        classInfo.methods().stream().map(DAOMethodInfo::rowRecord).filter(Objects::nonNull).forEach(s -> generator.generateRowRecord(classInfo, s));
    }
}
