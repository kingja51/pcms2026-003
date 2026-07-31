package com.gonet.primary.admin.mapper;

import com.gonet.primary.admin.dto.Admin;
import com.gonet.primary.admin.dto.AdminSearch;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * tb_admin CRUD + tb_admin_role / tb_admin_withdraw / tb_admin_password_history 보조.
 *
 * <p>{@code findList} 는 {@link Admin} 을 반환 — EncryptInterceptor 가 @Encrypt 필드 자동 복호화.
 * <p>role_ids CSV 재계산은 SP {@code sp_rebuild_admin_role_csv(admin_id)} 로 위임.
 */
@EgovMapper
public interface AdminMapper {

    // ------------------------------------------------------------------
    // 조회
    // ------------------------------------------------------------------

    List<Admin> findList(@Param("search") AdminSearch search);

    int countList(@Param("search") AdminSearch search);

    Admin findById(@Param("adminId") String adminId);

    int existsByLoginId(@Param("loginId") String loginId,
                        @Param("excludeAdminId") String excludeAdminId);

    int existsByEmailHash(@Param("emailHash") String emailHash,
                          @Param("excludeAdminId") String excludeAdminId);

    /** 관리자 직계 역할 ID 목록(tb_admin_role, delete_yn='N'). */
    List<String> findRoleIdsByAdminId(@Param("adminId") String adminId);

    // ------------------------------------------------------------------
    // CUD
    // ------------------------------------------------------------------

    void insert(Admin admin);

    void update(Admin admin);

    /** 관리자 본인 마이페이지용 self-service 프로필 수정 (이름/이메일/전화/조직정보). */
    void updateSelfProfile(Admin admin);

    void updatePassword(@Param("adminId") String adminId,
                         @Param("password") String password,
                         @Param("passwordChangedAt") java.time.LocalDateTime changedAt,
                         @Param("passwordExpireAt")  java.time.LocalDateTime expireAt);

    /** 로그인 성공 시 최종 로그인 일시/IP 및 실패 카운트 초기화. */
    void updateLastLogin(@Param("adminId") String adminId,
                          @Param("lastLoginAt") java.time.LocalDateTime lastLoginAt,
                          @Param("lastLoginIp") String lastLoginIp);

    /**
     * 로그인 실패 카운터 +1 + threshold 도달 시 locked_until 자동 설정 (원자적).
     * UPDATE 영향 행 수 반환 — 0이면 loginId 미존재 (enumeration 방지용으로 호출자 무시 권장).
     */
    int incrementLoginFailAndMaybeLock(@Param("loginId") String loginId,
                                        @Param("threshold") int threshold,
                                        @Param("lockMinutes") int lockMinutes);

    /** 특정 loginId 의 현재 login_fail_count. 없으면 0 반환. */
    Integer findLoginFailCountByLoginId(@Param("loginId") String loginId);

    /** 특정 loginId 의 현재 locked_until. 없거나 잠금 해제 상태면 null. */
    java.time.LocalDateTime findLockedUntilByLoginId(@Param("loginId") String loginId);

    /** 관리자에 의한 강제 잠금 해제 — locked_until=NULL, login_fail_count=0. 영향 행 수 반환. */
    int unlockAdmin(@Param("adminId") String adminId);

    /** 5회 실패 잠금 발동/해제 시점에 captcha_required_yn 토글. 로그인 성공 시 'N' 복귀. */
    int setCaptchaRequiredByLoginId(@Param("loginId") String loginId,
                                     @Param("yn") String yn);

    /** 로그인 폼 GET 단계에서 위젯 노출 여부 결정용. 'Y' 면 강제 CAPTCHA. 없으면 null. */
    String findCaptchaRequiredByLoginId(@Param("loginId") String loginId);

    /** 2FA secret 및 활성화 여부 갱신 — TwoFactorUsrController 셀프 등록용. */
    void updateTwoFactor(@Param("adminId") String adminId,
                          @Param("twoFactorSecret") String twoFactorSecret,
                          @Param("twoFactorEnabledYn") String twoFactorEnabledYn);

    int softDelete(@Param("adminId") String adminId);

    /** 관리자-역할 매핑 clear + 재등록 (soft-delete 후 UPSERT). */
    void clearRoles(@Param("adminId") String adminId);

    void insertRole(@Param("adminRoleId") String adminRoleId,
                     @Param("adminId") String adminId,
                     @Param("roleId") String roleId);

    /** 비밀번호 이력 적재. */
    void insertPasswordHistory(@Param("pwdHistoryId") String pwdHistoryId,
                                @Param("adminId") String adminId,
                                @Param("passwordHash") String passwordHash,
                                @Param("changedAt") java.time.LocalDateTime changedAt);

    /** 최근 비밀번호 해시 목록 — 재사용 금지 검사용. changed_at DESC N건. */
    List<String> findRecentPasswordHashes(@Param("adminId") String adminId,
                                            @Param("limit") int limit);

    /** 탈퇴 관리자 감사 행 삽입 (tb_admin_withdraw). */
    void insertWithdraw(@Param("admin") Admin admin,
                         @Param("reason") String reason,
                         @Param("withdrawnBy") String withdrawnBy,
                         @Param("retentionExpireAt") java.time.LocalDateTime retentionExpireAt);

    /** SP 호출: role_ids CSV 재계산. */
    void callRebuildRoleCsv(@Param("adminId") String adminId);

    /** role_ids 기반으로 tb_role.role_code 를 JOIN해 role_codes CSV 재계산. callRebuildRoleCsv 직후 호출. */
    void updateRoleCodes(@Param("adminId") String adminId);

    /**
     * 탈퇴 만료 영구 삭제 — {@code tb_admin_withdraw.retention_expire_at &lt;= now} 인 행 hard delete.
     * {@link com.gonet.scheduler.WithdrawPurgeScheduler} 가 매일 호출.
     * 관리자 보존 정책 — GoPCMS 운영 컨텍스트에서는 회원과 동일 1년 권고
     * (실제 퇴사·인사 데이터는 MIS 가 보유). 등록 시점에 retention_expire_at 부여.
     */
    int purgeExpiredWithdraw(@Param("now") java.time.LocalDateTime now);

    /** dry-run 카운트 — DELETE 없이 만료 후보 수만. */
    int countExpiredWithdraw(@Param("now") java.time.LocalDateTime now);
}
