package com.gonet.config.datasource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/**
 * Secondary DataSource — 개별 프로그램·외부 API 수집 영역.
 *
 * <p>수용 대상: 학과/교수 마스터(`tb_dept_master`·`tb_faculty`·`tb_faculty_program`),
 * 선거인명부(`tb_election_voter`·`tb_election_voter_import_job` — 2026-07-31 primary 에서 이관).
 *
 * <p>primary 와 상호 의존하지 않는다(ArchUnit R5). 사이트 범위 필터가 필요하면
 * DB 조인이 아니라 애플리케이션의 SiteContext 로 건다 — DB 가 달라 조인이 불가능하다.
 */
@Configuration
public class SecondaryDataSourceConfig {

    public static final String DATA_SOURCE     = "secondaryDataSource";
    public static final String TRANSACTION_MGR = "secondaryTransactionManager";

    @Bean(name = DATA_SOURCE, destroyMethod = "close")
    public DataSource secondaryDataSource(GopcmsDataSourceProperties props) {
        return DataSourceFactory.create(props.getSecondary(), "HikariSecondary");
    }

    @Bean(name = TRANSACTION_MGR)
    public PlatformTransactionManager secondaryTransactionManager(
            @Qualifier(DATA_SOURCE) DataSource secondaryDataSource) {
        return new DataSourceTransactionManager(secondaryDataSource);
    }
}
