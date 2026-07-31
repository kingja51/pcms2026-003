package com.gonet.config.flyway;

import com.gonet.config.datasource.LoggingDataSourceConfig;
import com.gonet.config.datasource.PrimaryDataSourceConfig;
import com.gonet.config.datasource.SecondaryDataSourceConfig;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * DataSource 별 Flyway 구성 — primary / secondary / logging 3개(D2).
 *
 * <p>Spring Boot 의 Flyway 자동설정을 쓸 수 없다. 이 프로젝트는 {@code spring.datasource} 가
 * 아니라 커스텀 DataSource 3개를 쓰므로, 각각에 대해 Flyway 빈을 명시 구성한다.
 *
 * <p><b>실행 순서</b> — 각 빈이 해당 DataSource 를 파라미터로 받으므로 Spring 이
 * "DataSource 생성 → Flyway migrate" 순서를 보장한다. MyBatis 의 SqlSessionFactory 는
 * 같은 DataSource 에 의존하지만 Flyway 빈과는 직접 의존이 없어 순서가 보장되지 않는다 —
 * P0 범위에서는 매퍼가 없어 문제되지 않으나, 순서 보장이 필요해지면
 * {@code @DependsOn} 을 SqlSessionFactory 쪽에 건다.
 *
 * <p><b>비활성 시 동작</b> — {@code gopcms.flyway.enabled=false} 면 {@code migrate()} 를
 * 호출하지 않고 빈만 만든다. 운영(prod)이 여기에 해당하며, 스키마 변경은 DBA 가
 * 같은 마이그레이션 파일을 Flyway CLI 로 집행한다(D1 ③).
 *
 * <p>위치: {@code classpath:db/migration/{primary,secondary,logging}/mariadb}.
 * 벤더 층을 남겨 둔 이유는 나중에 늘어나도 디렉터리 구조를 바꾸지 않기 위해서다(§6-5).
 */
@Configuration
@EnableConfigurationProperties(GopcmsFlywayProperties.class)
public class FlywayConfig {

    private static final Logger log = LoggerFactory.getLogger(FlywayConfig.class);

    @Bean
    public Flyway primaryFlyway(
            @Qualifier(PrimaryDataSourceConfig.DATA_SOURCE) DataSource dataSource,
            GopcmsFlywayProperties props) {
        return build(dataSource, props, "primary");
    }

    @Bean
    public Flyway secondaryFlyway(
            @Qualifier(SecondaryDataSourceConfig.DATA_SOURCE) DataSource dataSource,
            GopcmsFlywayProperties props) {
        return build(dataSource, props, "secondary");
    }

    @Bean
    public Flyway loggingFlyway(
            @Qualifier(LoggingDataSourceConfig.DATA_SOURCE) DataSource dataSource,
            GopcmsFlywayProperties props) {
        return build(dataSource, props, "logging");
    }

    private Flyway build(DataSource dataSource, GopcmsFlywayProperties props, String db) {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration/" + db + "/mariadb")
                .baselineOnMigrate(props.isBaselineOnMigrate())
                .baselineVersion(props.getBaselineVersion())
                .validateOnMigrate(props.isValidateOnMigrate())
                .outOfOrder(props.isOutOfOrder())
                .load();

        if (props.isEnabled()) {
            flyway.migrate();
        } else {
            log.info("FLYWAY_SKIP db={} — gopcms.flyway.enabled=false (운영은 DBA 가 CLI 로 집행)", db);
        }
        return flyway;
    }
}
