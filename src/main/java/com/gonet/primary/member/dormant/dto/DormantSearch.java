package com.gonet.primary.member.dormant.dto;

import com.gonet.common.dto.PageRequest;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 휴면 회원 검색 조건 (관리자 화면).
 *
 * <p>휴면 회원 도메인은 평문 PII 가 분리 보관됨 — 모든 PII 필드는 마스킹 후 노출.
 * 검색 키는 평문 검색 불가한 영역 (login_id / nickname 만 LIKE) + email_hash 정확 매칭.
 */
@Getter
@Setter
public class DormantSearch extends PageRequest {
    private String    siteId;
    private String    emailExact;       // HMAC 매칭
    private LocalDate dormantFrom;
    private LocalDate dormantTo;
}
