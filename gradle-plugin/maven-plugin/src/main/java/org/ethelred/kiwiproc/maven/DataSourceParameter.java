/* (C) Edward Harman 2026 */
package org.ethelred.kiwiproc.maven;

import java.io.File;
import org.jspecify.annotations.Nullable;

/** One {@code <dataSource>} entry inside the plugin's {@code <dataSources>} configuration. */
public class DataSourceParameter {
    private String name = "default";

    @Nullable private File liquibaseChangelog;

    @Nullable private String jdbcUrl;

    @Nullable private String database;

    @Nullable private String username;

    @Nullable private String password;

    @Nullable private String driverClassName;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public @Nullable File getLiquibaseChangelog() {
        return liquibaseChangelog;
    }

    public void setLiquibaseChangelog(@Nullable File liquibaseChangelog) {
        this.liquibaseChangelog = liquibaseChangelog;
    }

    public @Nullable String getJdbcUrl() {
        return jdbcUrl;
    }

    public void setJdbcUrl(@Nullable String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }

    public @Nullable String getDatabase() {
        return database;
    }

    public void setDatabase(@Nullable String database) {
        this.database = database;
    }

    public @Nullable String getUsername() {
        return username;
    }

    public void setUsername(@Nullable String username) {
        this.username = username;
    }

    public @Nullable String getPassword() {
        return password;
    }

    public void setPassword(@Nullable String password) {
        this.password = password;
    }

    public @Nullable String getDriverClassName() {
        return driverClassName;
    }

    public void setDriverClassName(@Nullable String driverClassName) {
        this.driverClassName = driverClassName;
    }

    public boolean isExternal() {
        return jdbcUrl != null;
    }

    public boolean isMySQL() {
        return "com.mysql.cj.jdbc.Driver".equals(driverClassName);
    }

    public boolean isH2() {
        return "org.h2.Driver".equals(driverClassName) || (jdbcUrl != null && jdbcUrl.startsWith("jdbc:h2:"));
    }
}
