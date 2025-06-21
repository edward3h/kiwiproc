/* (C) Edward Harman 2025 */
package org.ethelred.kiwiproc.gradle;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Optional;

public interface KiwiProcDataSource {
    String getName();

    @Optional
    RegularFileProperty getLiquibaseChangelog();

    @Optional
    Property<String> getJdbcUrl();

    @Optional
    Property<String> getDatabase();

    @Optional
    Property<String> getUsername();

    @Optional
    Property<String> getPassword();

    @Optional
    Property<String> getDriverClassName();
}
