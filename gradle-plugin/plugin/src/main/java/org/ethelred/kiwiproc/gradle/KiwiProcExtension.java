package org.ethelred.kiwiproc.gradle;

import org.ethelred.kiwiproc.processorconfig.DependencyInjectionStyle;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Optional;

public interface KiwiProcExtension {
    Property<DependencyInjectionStyle> getDependencyInjectionStyle();
    Property<Boolean> getDebug();
    @Optional
    RegularFileProperty getLiquibaseChangelog();
    NamedDomainObjectContainer<KiwiProcDataSource> getDataSources();
}
