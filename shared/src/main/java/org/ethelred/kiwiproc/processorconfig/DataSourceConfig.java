/* (C) Edward Harman 2024 */
package org.ethelred.kiwiproc.processorconfig;

import io.avaje.jsonb.Json;
import org.jspecify.annotations.Nullable;

@Json
public record DataSourceConfig(
        String named,
        String url,
        String database,
        String username,
        @Nullable String password,
        @Nullable String driverClassName) {}
