package com.gonet.config.shedlock;

import com.gonet.config.datasource.LoggingDataSourceConfig;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * ShedLock 분산 cron 락 — 다중 인스턴스 배포 시 같은 시각의 스케줄러 중복 실행 방지.
 *
 * <p><b>배경 (코드 리뷰 §C-3 회귀 가드)</b>: 기존 8 개 스케줄러는 단일 인스턴스 전제로
 * 설계됐으나 운영이 N 대 인스턴스로 확장되면 같은 cron tick 에 모두 진입하여 파일/DB 중복
 * 삭제, 중복 알림, 중복 API 호출 등 운영 사고로 직결.
 *
 * <p><b>저장소</b>: logging DB ({@code gopcms_logging.shedlock}). 본 테이블 1회 생성 DDL 은
 * {@code docs/ddl-requests/2026-05-12_shedlock_table.sql} 참조.
 *
 * <p><b>적용 정책</b>: {@code @Order LOWEST} 로 두지 않고 default — Spring AOP advisor 가
 * {@code @SchedulerLock} 어드바이스를 {@code @Scheduled} 인터셉터 안쪽에 끼워 lock 획득
 * 실패 시 메서드 body 가 통째로 skip.
 *
 * <p><b>defaultLockAtMostFor=PT30M</b>: 인스턴스가 비정상 종료되어 락 미해제 시 30 분 후
 * 다른 인스턴스가 대신 진입 가능. 개별 스케줄러는 더 짧거나 긴 값으로 override 권장 (예:
 * 짧은 batch 는 PT5M, 대형 retention 은 PT2H).
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT30M")
public class ShedLockConfig {

    /**
     * logging DB 기반 LockProvider. {@code shedlock} 테이블의 row 단위 락으로 다중 인스턴스
     * 진입을 직렬화. {@code usingDbTime()} 로 DB 시각을 기준 — 인스턴스 시계 드리프트에 둔감.
     */
    @Bean
    public LockProvider lockProvider(@Qualifier(LoggingDataSourceConfig.DATA_SOURCE) DataSource ds) {
        return new JdbcTemplateLockProvider(
            JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(ds))
                .usingDbTime()
                .build());
    }
}
