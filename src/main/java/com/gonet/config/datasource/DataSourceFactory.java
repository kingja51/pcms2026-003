package com.gonet.config.datasource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;

/**
 * HikariCP 기반 DataSource 생성 헬퍼.
 * Primary / Secondary / Logging 3개 Config 가 공유한다.
 *
 * <p>클래스명이 {@code Egov} 로 시작하지 않는다 — 호환성 규칙 7.
 * (실행환경 클래스를 상속하지 않으므로 규칙 대상 자체는 아니다)
 */
final class DataSourceFactory {

    private DataSourceFactory() {}

    static DataSource create(GopcmsDataSourceProperties.Node node, String defaultPoolName) {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(node.getJdbcUrl());
        cfg.setUsername(node.getUsername());
        cfg.setPassword(node.getPassword());
        // 정책 — 모든 프로파일 yml 이 driver-class-name 을 명시한다.
        // 비어 있으면 즉시 예외로 fail-fast — 의도된 안전장치다.
        cfg.setDriverClassName(node.getDriverClassName());
        cfg.setMaximumPoolSize(node.getMaximumPoolSize());
        cfg.setMinimumIdle(node.getMinimumIdle());
        cfg.setConnectionTimeout(node.getConnectionTimeout());
        cfg.setValidationTimeout(node.getValidationTimeout());
        cfg.setMaxLifetime(node.getMaxLifetime());
        cfg.setIdleTimeout(node.getIdleTimeout());
        cfg.setPoolName(node.getPoolName() != null ? node.getPoolName() : defaultPoolName);
        // autoCommit=false — 트랜잭션 경계를 Spring 이 전담한다.
        cfg.setAutoCommit(false);
        cfg.setConnectionTestQuery("SELECT 1");
        return new HikariDataSource(cfg);
    }
}
