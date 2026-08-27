/* (C) Edward Harman 2025 */
package org.ethelred.kiwiproc.meta;

import org.ethelred.kiwiproc.processorconfig.DataSourceConfig;
import org.ethelred.kiwiproc.processorconfig.DatabaseKind;

public class DatabaseDialects {
    public static DatabaseDialect fromConfig(DataSourceConfig config) {
        return switch (DatabaseKind.fromConfig(config)) {
            case MYSQL -> new MySQLDialect();
            case H2 -> new H2Dialect();
            case POSTGRES -> new PostgresDialect();
        };
    }
}
