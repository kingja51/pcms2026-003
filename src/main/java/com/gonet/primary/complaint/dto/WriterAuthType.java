package com.gonet.primary.complaint.dto;

public enum WriterAuthType {
    /** 일반 회원 로그인 — writer_member_id 필수 */
    MEMBER,
    /** OAuth2 소셜 로그인 — writer_oauth_provider + writer_oauth_uid_hash 필수 */
    OAUTH2,
    /** NICE 실명인증 단독 — writer_di_hash 필수 */
    NICE
}
