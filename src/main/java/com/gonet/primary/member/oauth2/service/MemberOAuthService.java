package com.gonet.primary.member.oauth2.service;

import com.gonet.primary.member.dto.JoinSessionData;
import com.gonet.primary.member.dto.MemberJoinForm;
import com.gonet.primary.member.oauth2.dto.ExternalProfile;
import com.gonet.primary.member.oauth2.dto.MemberOAuth;
import com.gonet.primary.system.login.dto.UserLogin;

/**
 * 회원 OAuth2 매핑 서비스 — {@code tb_member_oauth} CRUD + 자동 로그인을 위한 v_user_login 조회.
 *
 * <p>OAuth2 흐름의 외부 API 호출은 {@link OAuth2Service} 가, 매핑 DB 조작은 본 서비스가
 * 담당한다. 컨트롤러는 두 서비스만 주입하고 Mapper 직접 호출 금지 (eGov §4.1, ArchUnit R1).
 */
public interface MemberOAuthService {

    /**
     * provider + providerUserId 로 매핑 조회.
     *
     * @return 미존재 시 null
     */
    MemberOAuth findByProviderUser(String provider, String providerUserId);

    /** 매핑 신규 등록 — provider × providerUserId UNIQUE 위반 시 호출 측 책임. */
    void link(MemberOAuth link);

    /** 마지막 로그인 시각을 현재 시각으로 갱신. */
    void updateLastLogin(String memberOauthId);

    /**
     * 매핑 soft delete — 회원이 탈퇴되었지만 매핑이 stale 한 경우 콜백에서 자동 정리.
     * 같은 provider 계정으로 재가입 시 신규 매핑을 INSERT 할 수 있도록 길을 열어 둔다.
     */
    void deactivate(String memberOauthId);

    /**
     * 회원(MEMBER) 자동 로그인 직후 SecurityContext 구성을 위한 {@code v_user_login} VIEW 조회.
     * user_type 화이트리스트는 {@code MEMBER} 만 — 관리자/직원 계정은 본 경로로 진입할 수 없다.
     *
     * @return 미존재 시 null (호출 측이 폴백 처리)
     */
    UserLogin loadMemberUserLogin(String loginId);

    /**
     * OAuth2 가입 + 매핑 INSERT 를 단일 트랜잭션으로 묶는다.
     *
     * <p>흐름:
     * <ol>
     *   <li>{@link com.gonet.primary.member.service.MemberService#join} 호출 (Propagation.REQUIRED — 본 트랜잭션에 합류)</li>
     *   <li>{@code tb_member_oauth} 매핑 INSERT</li>
     *   <li>{@code OAUTH2_LINK} 감사 이벤트 발행</li>
     * </ol>
     * 매핑 INSERT 가 실패하면 회원 가입도 함께 롤백 — "회원은 만들어졌지만 OAuth 매핑이 없는"
     * 부분 실패를 차단. 컨트롤러는 본 메서드 1번 호출로 가입 흐름 완결.
     *
     * @return 신규 가입된 회원의 memberId (UUID v7)
     * @throws IllegalArgumentException 가입 폼 검증 실패 등 사용자 입력 오류
     */
    String joinAndLink(MemberJoinForm form,
                        JoinSessionData sess,
                        ExternalProfile profile,
                        String siteId,
                        String clientIp,
                        String userAgent);
}
