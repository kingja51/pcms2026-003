package com.gonet.primary.member.service;

import com.gonet.primary.member.dto.JoinSessionData;
import com.gonet.primary.member.dto.Member;
import com.gonet.primary.member.dto.MemberConsent;
import com.gonet.primary.member.dto.MemberJoinForm;
import com.gonet.primary.member.dto.MemberPasswordForm;
import com.gonet.primary.member.dto.MemberProfileForm;
import com.gonet.primary.member.dto.MemberSearch;

import java.util.List;

public interface MemberService {

    // 조회 (관리자용)
    List<Member> search(MemberSearch search);
    int          count(MemberSearch search);
    Member       get(String memberId);

    // 회원 가입 (비로그인)
    /**
     * 회원 가입 실행.
     *
     * @param form     폼 입력 (userType, 이름/이메일/비번/부모이름 등)
     * @param joinSess 본인인증 결과(세션). 본인(ADULT)/부모(CHILD) DI + 이름 + 휴대폰.
     *                 CHILD 의 경우 이 DI 가 자녀의 di/di_hash + parent_di/parent_di_hash 양쪽에 저장된다.
     * @return 발급된 memberId
     */
    String join(MemberJoinForm form, JoinSessionData joinSess,
                 String siteId, String clientIp, String userAgent);

    /**
     * 아이디 중복확인. true=이미 사용 중, false=사용 가능.
     * 정규화(소문자 trim) 후 조회.
     */
    boolean existsLoginId(String siteId, String loginId);

    // 아이디·비밀번호 찾기 (비로그인)
    /**
     * 이메일로 회원 조회. 일치하는 회원이 없으면 null 반환 (enumeration 보호는 호출자 책임).
     *
     * <p>주의 — /member/find-id 외부 화면은 이메일 단독 매칭이 enumeration 위험이 있어
     * {@link #findIdByNameAndEmail(String, String, String)} 사용 권장 (2026-05-25 강화).
     * 본 메서드는 내부 도메인 (가입 중복 검증 등) 에서만 사용.
     */
    Member findByEmail(String siteId, String email);

    /**
     * 이름 + 이메일 동시 일치 시에만 회원 반환. /member/find-id 강화 (2026-05-25).
     *
     * <p>SQL 단에서 {@code member_name = ? AND email_hash = ?} AND 매칭. 둘 중 하나라도
     * 불일치 시 null. enumeration 보호는 호출자가 generic 메시지로 처리.
     *
     * @param siteId  사이트 ID (null 허용 — 단일 사이트 가입 가정 시 무관)
     * @param name    가입 시 등록한 이름 (member_name 평문 컬럼과 직접 비교)
     * @param email   가입 시 등록한 이메일 (email_hash HMAC 매칭)
     */
    Member findIdByNameAndEmail(String siteId, String name, String email);

    /**
     * 로그인 ID + 이메일로 본인확인 후 임시 비밀번호 발송.
     * 일치하지 않으면 false 반환 (enumeration 보호는 호출자 책임).
     *
     * @return 본인확인·메일 발송까지 성공하면 true
     */
    boolean selfResetPassword(String siteId, String loginId, String email);

    // 마이페이지 (본인)
    void updateProfile(String memberId, MemberProfileForm form);
    void changePassword(String memberId, MemberPasswordForm form);
    /** 본인 탈퇴. */
    void withdraw(String memberId, String reason);

    /**
     * 마이페이지 민감 페이지 진입 전 재인증 — 3요소 동시 일치 시 성공:
     * <ul>
     *   <li>이름(평문 일치)</li>
     *   <li>이메일(HMAC-SHA256 해시 비교, {@code tb_member.email_hash})</li>
     *   <li>비밀번호(BCrypt)</li>
     * </ul>
     *
     * <p>CI 등 본인확인 서비스는 현재 미연동 — 이메일 경로가 대체 신원 축.
     * <p>타이밍 공격 완화를 위해 회원이 존재하지 않거나 이름/이메일 불일치 시에도
     *    BCrypt 검증(dummy)을 수행해 응답 시간 균일화.
     *
     * @return 모든 항목 일치 시 true, 그 외 false
     */
    boolean verifyReauth(String memberId, String memberName, String email, String password);

    // 동의 이력
    List<MemberConsent> listConsents(String memberId);

    // 관리자 강제 수정·삭제
    void adminUpdateStatus(String memberId, String status);
    void adminSoftDelete(String memberId, String reason);
    /** 회원 계정 잠금 강제 해제 — login_fail_count=0, locked_until=NULL. */
    void adminUnlock(String memberId);

    /**
     * 관리자에 의한 비밀번호 초기화 + 회원 이메일로 임시 비밀번호 발송.
     *
     * <p>평문 비밀번호는 로그·감사·예외 메시지에 절대 기록하지 않는다.
     * 메일 발송 실패 시 전체 트랜잭션 롤백.
     */
    void resetPasswordAndSendMail(String memberId);
}
