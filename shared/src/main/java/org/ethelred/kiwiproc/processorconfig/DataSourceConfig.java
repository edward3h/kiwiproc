package org.ethelred.kiwiproc.processorconfig;

import io.avaje.jsonb.Json;

@Json
public record DataSourceConfig(String named, String url, String database, String username, String password, String driverClassName) {
}
