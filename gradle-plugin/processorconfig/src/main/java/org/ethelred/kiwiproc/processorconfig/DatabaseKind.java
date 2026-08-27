/* (C) Edward Harman 2026 */
package org.ethelred.kiwiproc.processorconfig;

import org.jspecify.annotations.Nullable;

/**
 * Identifies which database a {@link DataSourceConfig} (or a plugin-frontend-specific
 * datasource declaration) targets, by driver class name and/or JDBC URL prefix.
 *
 * <p>This is a pure classification enum with no JDBC driver dependency, so it can be used from
 * modules (like the Maven plugin) that must not pull every supported database's driver onto
 * their classpath just to answer "which database is this." Modules that need to do real,
 * driver-specific work per database (see {@code DatabaseDialect} in {@code querymeta}) switch on
 * this enum rather than re-deriving the classification from strings themselves.
 */
public enum DatabaseKind {
    POSTGRES(null, null),
    MYSQL("com.mysql.cj.jdbc.Driver", "jdbc:mysql:"),
    H2("org.h2.Driver", "jdbc:h2:");

    private final @Nullable String driverClassName;
    private final @Nullable String urlPrefix;

    DatabaseKind(@Nullable String driverClassName, @Nullable String urlPrefix) {
        this.driverClassName = driverClassName;
        this.urlPrefix = urlPrefix;
    }

    /**
     * The canonical JDBC driver class name for this kind, or {@code null} for {@link #POSTGRES}
     * (which has historically been left unset in generated {@link DataSourceConfig}s).
     */
    public @Nullable String driverClassName() {
        return driverClassName;
    }

    /**
     * Classifies a database given its driver class name and/or JDBC URL. Driver-class match is
     * checked first (across all kinds), then URL-prefix match (across all kinds), then falls
     * back to {@link #POSTGRES}. Either argument may be {@code null}.
     *
     * <p>Because driver-class match is checked across all kinds before URL-prefix match is
     * considered at all, a (realistic-but-contradictory) input that pairs one kind's driver class
     * with a different kind's URL prefix — e.g. {@code driverClassName="org.h2.Driver"} with
     * {@code url="jdbc:mysql://host/db"} — always resolves to the kind matched by the driver
     * class, regardless of the URL. This is a deliberate simplification: the three call sites
     * this enum replaces did not even agree with each other on priority order for such inputs
     * (two checked MySQL-before-H2, one checked H2-before-MySQL), so there was no single
     * behaviour to preserve for that edge case. A real configuration should never pair one
     * database's driver class with another database's URL prefix.
     */
    public static DatabaseKind fromDriverAndUrl(@Nullable String driverClassName, @Nullable String url) {
        for (var kind : values()) {
            if (kind.driverClassName != null && kind.driverClassName.equals(driverClassName)) {
                return kind;
            }
        }
        var effectiveUrl = url != null ? url : "";
        for (var kind : values()) {
            if (kind.urlPrefix != null && effectiveUrl.startsWith(kind.urlPrefix)) {
                return kind;
            }
        }
        return POSTGRES;
    }

    /** Convenience overload for classifying an existing {@link DataSourceConfig}. */
    public static DatabaseKind fromConfig(DataSourceConfig config) {
        return fromDriverAndUrl(config.driverClassName(), config.url());
    }
}
