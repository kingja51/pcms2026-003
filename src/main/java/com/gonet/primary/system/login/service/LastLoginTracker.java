package com.gonet.primary.system.login.service;

import com.gonet.primary.admin.mapper.AdminMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 로그인 성공 시 사용자 테이블의 {@code last_login_at} / {@code last_login_ip} 갱신 디스패처.
 *
 * <p>userType 에 따라 도메인 Mapper 로 위임한다. 업데이트가 실패해도 로그인 흐름은 계속 진행한다 —
 * 로그인 성공 판정은 이미 끝났고, 이 갱신은 통계·감사 보조 목적이다.
 *
 * <p><b>REQUIRES_NEW</b> 로 격리한다. 본 트랜잭션이 롤백돼도 로그인 기록은 남아야 한다.
 *
 * <h2>범위</h2>
 * <ul>
 *   <li><b>STAFF</b>(관리자) — P2 에서 구현</li>
 *   <li><b>MEMBER</b> — P5 에서 추가한다. 회원 도메인({@code MemberMapper}·{@code DormantMapper})이
 *       아직 없다. 001 은 여기서 휴면 전환 알림 이력({@code deleteNoticesByMemberId})도 정리한다</li>
 *   <li><s>EMPLOYEE</s> — <b>영구 제외</b>. 직원은 로그인 주체가 아니다(D7, 2026-07-31).
 *       {@code v_user_login} 에도 EMPLOYEE 절이 없다</li>
 * </ul>
 */
@Component
public class LastLoginTracker {

    private static final Logger log = LoggerFactory.getLogger(LastLoginTracker.class);

    private final AdminMapper adminMapper;

    public LastLoginTracker(AdminMapper adminMapper) {
        this.adminMapper = adminMapper;
    }

    /**
     * 사용자 타입별 최종 로그인 정보 갱신.
     *
     * @param userType MEMBER / STAFF ({@code v_user_login.user_type})
     * @param userId   UUID PK ({@code tb_admin.admin_id} / {@code tb_member.member_id})
     * @param clientIp 접속 IP
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW,
        transactionManager = com.gonet.config.datasource.PrimaryDataSourceConfig.TRANSACTION_MGR)
    public void track(String userType, String userId, String clientIp) {
        if (userType == null || userId == null) return;
        LocalDateTime now = LocalDateTime.now();
        try {
            switch (userType) {
                case "STAFF" -> adminMapper.updateLastLogin(userId, now, clientIp);
                // MEMBER 는 P5 에서 추가한다 — 그때까지는 아래 warn 으로 떨어진다.
                default      -> log.warn("LastLoginTracker: 미지원 userType={} userId={}", userType, userId);
            }
        } catch (Exception ex) {
            // 로그인 판정은 이미 성공했다. 갱신 실패가 로그인 흐름을 막지 않도록 삼킨다.
            log.warn("LastLoginTracker update failed type={} userId={} reason={}",
                userType, userId, ex.getMessage());
        }
    }
}
