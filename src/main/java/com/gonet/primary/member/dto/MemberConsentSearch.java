package com.gonet.primary.member.dto;

import com.gonet.common.dto.PageRequest;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 회원 동의 이력 검색 조건 (관리자 화면) — {@link PageRequest} 상속.
 *
 * <p>memberId 가 명시되면 단일 회원의 이력만, 비어 있으면 전체.
 * consentType — TERMS / PRIVACY / MARKETING / SMS / EMAIL / THIRD_PARTY.
 * agreeYn — Y / N. agreedAt 범위 필터 가능.
 */
@Getter
@Setter
public class MemberConsentSearch extends PageRequest {

    private String    memberId;
    private String    consentType;
    private String    agreeYn;
    private LocalDate agreedFrom;
    private LocalDate agreedTo;
}
