package com.gonet.primary.admin.dto;

import com.gonet.common.dto.PageRequest;
import lombok.Getter;
import lombok.Setter;

/**
 * 관리자 목록 검색 조건 — {@link PageRequest} 상속.
 *
 * <p>{@code keyword} 는 평문 검색 가능한 컬럼만 대상: login_id / employee_no / department
 * — admin_name/email 은 PII 암호화라 서버에서 매칭 불가
 * (email 정확검색은 {@code emailExact} HMAC 경로로 별도 지원).
 */
@Getter
@Setter
public class AdminSearch extends PageRequest {

    /** 완전일치 이메일 검색 → HMAC 매칭 */
    private String emailExact;

    /** ACTIVE/LOCKED/INACTIVE/SUSPENDED */
    private String status;

    private String adminGroupId;
}
