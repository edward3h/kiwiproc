/* (C) Edward Harman 2025 */
package org.ethelred.kiwiproc.meta;

import org.ethelred.kiwiproc.processorconfig.DataSourceConfig;

public class DatabaseDialects {
    public static DatabaseDialect fromConfig(DataSourceConfig config) {
        var driver = config.driverClassName();
        var url = config.url() != null ? config.url() : "";
        if ("com.mysql.cj.jdbc.Driver".equals(driver) || url.startsWith("jdbc:mysql:")) {
            return new MySQLDialect();
        }
        return new PostgresDialect();
    }
}
