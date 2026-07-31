package com.gonet.primary.member.oauth2.mapper;

import com.gonet.primary.member.oauth2.dto.MemberOAuth;
import com.gonet.primary.member.oauth2.dto.MemberOAuthSearch;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * tb_member_oauth Mapper — provider × providerUserId UNIQUE 매핑 CRUD + 관리자 조회.
 */
@EgovMapper
public interface MemberOAuthMapper {

    /** provider + providerUserId 로 매핑 조회 — 미존재 시 null. delete_yn='N' 만. */
    MemberOAuth findByProviderUser(@Param("provider") String provider,
                                    @Param("providerUserId") String providerUserId);

    /**
     * delete_yn 무관 매핑 조회 — soft-deleted row 도 포함.
     * UNIQUE (provider, provider_user_id) 가 delete_yn 을 구분하지 않으므로,
     * 재가입 시 stale row 가 있으면 reactivate 해야 한다.
     */
    MemberOAuth findAnyByProviderUser(@Param("provider") String provider,
                                       @Param("providerUserId") String providerUserId);

    /**
     * soft-deleted 매핑을 새 회원으로 reactivate — 같은 provider 계정 재가입 경로.
     * delete_yn='N', use_yn='Y', member_id/linked_at/last_login_at/email/name 갱신.
     */
    int reactivate(@Param("memberOauthId") String memberOauthId,
                    @Param("memberId") String memberId,
                    @Param("emailAtLink") String emailAtLink,
                    @Param("nameAtLink") String nameAtLink,
                    @Param("at") LocalDateTime at);

    /** 동일 회원 동일 provider 매핑 존재 여부 — true 면 이미 연결됨. */
    int countByMemberAndProvider(@Param("memberId") String memberId,
                                  @Param("provider") String provider);

    void insert(MemberOAuth m);

    void updateLastLogin(@Param("memberOauthId") String memberOauthId,
                          @Param("lastLoginAt") LocalDateTime at);

    /**
     * 매핑 soft delete — 회원이 탈퇴(soft delete)되었지만 매핑이 stale 한 경우
     * 콜백에서 자동 정리. 같은 provider 계정으로 재가입 시 신규 매핑을 INSERT 할 수 있다.
     */
    int softDeleteById(@Param("memberOauthId") String memberOauthId);

    // ------------------------------------------------------------------
    // 관리자 조회
    // ------------------------------------------------------------------

    List<MemberOAuth> findList(@Param("search") MemberOAuthSearch search);

    int countList(@Param("search") MemberOAuthSearch search);

    MemberOAuth findById(@Param("memberOauthId") String memberOauthId);

    List<MemberOAuth> findForExport(@Param("search") MemberOAuthSearch search);
}
