package org.ethelred.kiwiproc.processorconfig;

import io.avaje.jsonb.Json;
import java.util.Map;
import java.util.Objects;

@Json
public record ProcessorConfig(
        Map<String, DataSourceConfig> dataSources, DependencyInjectionStyle dependencyInjectionStyle) {
    public ProcessorConfig {
        dataSources = Objects.requireNonNullElse(dataSources, Map.of());
        dependencyInjectionStyle =
                Objects.requireNonNullElse(dependencyInjectionStyle, DependencyInjectionStyle.JAKARTA);
    }

    public static final ProcessorConfig EMPTY = new ProcessorConfig(Map.of(), DependencyInjectionStyle.JAKARTA);
}
