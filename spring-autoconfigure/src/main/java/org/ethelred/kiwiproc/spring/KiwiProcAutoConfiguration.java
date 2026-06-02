/* (C) Edward Harman 2026 */
package org.ethelred.kiwiproc.spring;

import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = DataSourceAutoConfiguration.class)
public class KiwiProcAutoConfiguration {

    @Bean("default")
    @ConditionalOnMissingBean(name = "default")
    @ConditionalOnSingleCandidate(DataSource.class)
    public DataSource kiwiProcDefaultDataSource(DataSource dataSource) {
        return dataSource;
    }
}
