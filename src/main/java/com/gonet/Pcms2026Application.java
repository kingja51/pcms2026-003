package com.gonet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * PCMS 2026-003 Application Entry Point.
 *
 * <p>3-DB DataSource 를 수동 구성(config.datasource) 하므로
 * Spring Boot 의 기본 {@link DataSourceAutoConfiguration} 을 제외한다.
 *
 * <p><b>이중 진입점</b>:
 * <ul>
 *   <li><b>외부 Tomcat 배포 (운영 기본)</b> — {@link SpringBootServletInitializer}
 *       상속. war 패키징되어 Tomcat 의 ServletContainerInitializer 가 자동 부팅.
 *       {@link #configure} 가 SpringApplicationBuilder 에 본 클래스를 등록.</li>
 *   <li><b>로컬 임베디드 실행</b> — {@link #main} 이 직접 SpringApplication.run.
 *       provided 스코프의 spring-boot-starter-tomcat 이 IDE 의 runtime classpath 에
 *       자동 포함되어 임베디드 Tomcat 으로 부팅. NICE 모듈 플래그는 Run
 *       Configuration 의 VM options 로 부여.</li>
 * </ul>
 *
 * <p>두 진입점 모두 동일한 ApplicationContext 를 구성 — 도메인 코드/설정은 무영향.
 *
 * <p><b>호환성</b>: 실행환경 클래스를 상속하지 않으므로 규칙 7(확장 클래스 명명)의
 * 대상이 아니다. 클래스명이 {@code Egov} 로 시작하지 않는 점도 함께 만족한다.
 */
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
@EnableCaching
@EnableAsync
@EnableScheduling
@EnableTransactionManagement
public class Pcms2026Application extends SpringBootServletInitializer {

    /**
     * 외부 Tomcat 부팅 진입점. Tomcat 이 war 의 META-INF/services/jakarta.servlet.
     * ServletContainerInitializer 를 통해 SpringBootServletInitializer 를 찾고 본 메서드를 호출.
     */
    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(Pcms2026Application.class);
    }

    /**
     * 로컬 실행 / mvn spring-boot:run 진입점.
     * 임베디드 Tomcat (10.1.x — pom.xml 의 tomcat.version property 로 강제) 으로 부팅.
     */
    public static void main(String[] args) {
        SpringApplication.run(Pcms2026Application.class, args);
    }
}
