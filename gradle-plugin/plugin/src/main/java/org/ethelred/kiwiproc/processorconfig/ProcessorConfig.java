/* (C) Edward Harman 2024 */
package org.ethelred.kiwiproc.processorconfig;

import io.avaje.jsonb.Json;
import java.util.Map;
import java.util.Objects;

@Json
public record ProcessorConfig(
        Map<String, DataSourceConfig> dataSources, DependencyInjectionStyle dependencyInjectionStyle, boolean debug) {
    public ProcessorConfig {
        dataSources = Objects.requireNonNullElse(dataSources, Map.of());
        dependencyInjectionStyle =
                Objects.requireNonNullElse(dependencyInjectionStyle, DependencyInjectionStyle.JAKARTA);
    }

    public ProcessorConfig(
            Map<String, DataSourceConfig> dataSources, DependencyInjectionStyle dependencyInjectionStyle) {
        this(dataSources, dependencyInjectionStyle, false);
    }

    public static final ProcessorConfig EMPTY = new ProcessorConfig(Map.of(), DependencyInjectionStyle.JAKARTA, false);
}
