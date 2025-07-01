/* (C) Edward Harman 2024 */
package org.ethelred.kiwiproc.processorconfig;

import io.avaje.jsonb.Json;
import org.jspecify.annotations.Nullable;

@Json
public record DataSourceConfig(
        String named,
        String url,
        @Nullable String database,
        @Nullable String username,
        @Nullable String password,
        @Nullable String driverClassName) {}
