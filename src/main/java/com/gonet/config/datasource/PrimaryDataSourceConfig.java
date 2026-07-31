package com.gonet.config.datasource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/**
 * Primary DataSource — {@code tb_*} 업무 테이블(회원·관리자·콘텐츠·게시판·파일·RBAC 등).
 *
 * <p>3개 DataSource 중 {@code @Primary} 대상이다. Qualifier 없는 {@code DataSource} 주입은
 * 이 빈을 선택한다. 의도치 않은 primary 주입을 막기 위해 Secondary/Logging 쪽은
 * {@link Qualifier} 명시를 강제한다.
 *
 * <p><b>트랜잭션 주의</b> — {@code @Transactional} 에 {@code transactionManager} 를 반드시 명시한다.
 * 미지정 시 {@code @Primary} 인 이 매니저에 붙어 엉뚱한 DB 트랜잭션이 열린다.
 */
@Configuration
@EnableConfigurationProperties(GopcmsDataSourceProperties.class)
public class PrimaryDataSourceConfig {

    public static final String DATA_SOURCE     = "primaryDataSource";
    public static final String TRANSACTION_MGR = "primaryTransactionManager";

    @Primary
    @Bean(name = DATA_SOURCE, destroyMethod = "close")
    public DataSource primaryDataSource(GopcmsDataSourceProperties props) {
        return DataSourceFactory.create(props.getPrimary(), "HikariPrimary");
    }

    @Primary
    @Bean(name = TRANSACTION_MGR)
    public PlatformTransactionManager primaryTransactionManager(
            @Qualifier(DATA_SOURCE) DataSource primaryDataSource) {
        return new DataSourceTransactionManager(primaryDataSource);
    }
}
