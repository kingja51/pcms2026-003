package com.gonet.config.datasource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/**
 * Logging DataSource — {@code log_*} · {@code stat_*} · {@code shedlock}.
 *
 * <p>primary → logging 의존은 허용한다(감사 다중 경로, ArchUnit R5 예외).
 *
 * <p><b>트랜잭션 주의</b> — 로그 기록은 {@code REQUIRES_NEW} 로 격리한다.
 * 본 트랜잭션이 롤백돼도 감사·보안 로그는 남아야 한다.
 */
@Configuration
public class LoggingDataSourceConfig {

    public static final String DATA_SOURCE     = "loggingDataSource";
    public static final String TRANSACTION_MGR = "loggingTransactionManager";

    @Bean(name = DATA_SOURCE, destroyMethod = "close")
    public DataSource loggingDataSource(GopcmsDataSourceProperties props) {
        return DataSourceFactory.create(props.getLogging(), "HikariLogging");
    }

    @Bean(name = TRANSACTION_MGR)
    public PlatformTransactionManager loggingTransactionManager(
            @Qualifier(DATA_SOURCE) DataSource loggingDataSource) {
        return new DataSourceTransactionManager(loggingDataSource);
    }
}
