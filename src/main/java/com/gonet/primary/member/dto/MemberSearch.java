package com.gonet.primary.member.dto;

import com.gonet.common.dto.PageRequest;
import lombok.Getter;
import lombok.Setter;

/**
 * 회원 목록 검색 조건 (관리자 화면) — {@link PageRequest} 상속.
 *
 * <p>{@code keyword}: 평문 검색 가능한 컬럼(login_id / nickname) 만 대상.
 * 이메일/이름은 PII 암호화되므로 {@code emailExact} HMAC 경로 또는 보지 않음.
 */
@Getter
@Setter
public class MemberSearch extends PageRequest {

    private String emailExact;      // HMAC 매칭
    private String status;
    private String siteId;
    private String joinType;
}
