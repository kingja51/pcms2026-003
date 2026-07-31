package com.gonet.primary.member.oauth2.dto;

import com.gonet.common.base.BaseEntity;
import com.gonet.common.base.SoftDeletable;
import com.gonet.common.base.UseFlagged;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * tb_member_oauth 1행 — provider × providerUserId UNIQUE 매핑.
 *
 * <p>email/name 은 연결 시점 스냅샷 (감사용). 이후 provider 변경된 값은 본 테이블에 반영하지 않음.
 */
@Getter
@Setter
public class MemberOAuth extends BaseEntity implements SoftDeletable, UseFlagged {

    private String        memberOauthId;
    private String        memberId;
    private String        provider;          // NAVER/KAKAO/GOOGLE
    private String        providerUserId;
    private String        emailAtLink;
    private String        nameAtLink;
    private LocalDateTime linkedAt;
    private LocalDateTime lastLoginAt;
    private String        useYn;
    private String        deleteYn;
}
