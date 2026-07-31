package com.gonet.config.flyway;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yml 의 {@code gopcms.flyway.*} 바인딩.
 *
 * <p>Spring Boot 의 {@code spring.flyway.*} 자동설정은 쓸 수 없다 —
 * 이 프로젝트는 {@code spring.datasource} 가 아니라 커스텀 DataSource 3개를 쓰기 때문이다.
 *
 * <p><b>실행 주체</b>(PLAN §6 D1 ③): local·dev 는 앱이 기동 시 실행하고,
 * 운영은 DBA 가 같은 마이그레이션 파일을 Flyway CLI 로 집행한다.
 * {@code application-prod.yml} 의 {@code enabled} 는 환경변수 override 없이 false 로 고정돼 있다.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "gopcms.flyway")
public class GopcmsFlywayProperties {

    /** 앱이 마이그레이션을 실행할지 여부. 운영은 false 고정. */
    private boolean enabled = false;

    /** 기존 스키마 도입 — 현재 상태를 베이스라인으로 잡는다. */
    private boolean baselineOnMigrate = true;

    private String baselineVersion = "0";

    private boolean validateOnMigrate = true;

    /** false 고정 — 버전을 건너뛴 적용을 막는다. */
    private boolean outOfOrder = false;
}
