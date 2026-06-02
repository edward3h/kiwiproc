/* (C) Edward Harman 2026 */
package org.ethelred.kiwiproc.spring;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class KiwiProcAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(
                    AutoConfigurations.of(DataSourceAutoConfiguration.class, KiwiProcAutoConfiguration.class));

    @Test
    void registersDefaultDataSourceAlias() {
        contextRunner
                .withPropertyValues("spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1")
                .run(context -> {
                    assertThat(context).hasBean("default");
                    assertThat(context.getBean("default")).isInstanceOf(DataSource.class);
                });
    }

    @Test
    void doesNotOverrideExplicitDefaultBean() {
        contextRunner
                .withPropertyValues("spring.datasource.url=jdbc:h2:mem:testdb2;DB_CLOSE_DELAY=-1")
                .withUserConfiguration(ExplicitDefaultBeanConfig.class)
                .run(context -> {
                    assertThat(context).hasBean("default");
                    // the explicit bean should be present, not ours
                    assertThat(context.getBean("default")).isNotInstanceOf(DataSource.class);
                });
    }

    @Test
    void doesNotRunWithoutDataSource() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(KiwiProcAutoConfiguration.class))
                .run(context -> assertThat(context).doesNotHaveBean("default"));
    }

    @org.springframework.context.annotation.Configuration
    static class ExplicitDefaultBeanConfig {
        @org.springframework.context.annotation.Bean("default")
        public String defaultBean() {
            return "explicit-default";
        }
    }
}
