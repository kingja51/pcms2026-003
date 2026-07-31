package com.gonet.config.datasource;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yml 의 {@code gopcms.datasource.*} 바인딩.
 *
 * <pre>
 * gopcms:
 *   datasource:
 *     primary:   { jdbc-url, username, password, driver-class-name, ... }
 *     secondary: { ... }
 *     logging:   { ... }
 * </pre>
 *
 * <p>Spring Boot 의 {@code spring.datasource} 자동설정을 쓰지 않는다 —
 * DataSource 가 3개라 자동설정으로는 표현할 수 없다.
 * {@code Pcms2026Application} 에서 {@code DataSourceAutoConfiguration} 을 제외한 이유다.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "gopcms.datasource")
public class GopcmsDataSourceProperties {

    private Node primary   = new Node();
    private Node secondary = new Node();
    private Node logging   = new Node();

    @Getter
    @Setter
    public static class Node {
        private String jdbcUrl;
        private String username;
        private String password;
        /** 비어 있으면 DataSourceFactory 가 fail-fast 한다. 전 프로파일 yml 이 명시한다. */
        private String driverClassName    = "";
        private int    maximumPoolSize    = 10;
        private int    minimumIdle        = 2;
        private long   connectionTimeout  = 5_000L;
        private long   validationTimeout  = 3_000L;
        private long   maxLifetime        = 1_800_000L;
        private long   idleTimeout        = 600_000L;
        private String poolName;
    }
}
