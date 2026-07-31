package com.gonet.primary.member.oauth2.dto;

import com.gonet.common.dto.PageRequest;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 회원 OAuth 매핑 검색 조건 (관리자 화면).
 *
 * <p>provider — NAVER / KAKAO / GOOGLE. memberId 명시 시 단일 회원 매핑.
 */
@Getter
@Setter
public class MemberOAuthSearch extends PageRequest {
    private String    memberId;
    private String    provider;
    private LocalDate linkedFrom;
    private LocalDate linkedTo;
    private String    useYn;
}
