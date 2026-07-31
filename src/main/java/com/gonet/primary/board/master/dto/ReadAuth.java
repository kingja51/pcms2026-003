package com.gonet.primary.board.master.dto;

/**
 * 게시판 읽기 권한 카탈로그 — {@code tb_bbs_master.read_auth} 와 동기화.
 *
 * <p>의미 (BoardArticleServiceImpl.ensureCanRead 와 정합):
 * <ul>
 *   <li>{@link #ALL}      — 누구나 (anonymous 포함)</li>
 *   <li>{@link #MEMBER}   — 로그인 사용자 (회원/직원/관리자 모두 통과)</li>
 *   <li>{@link #EMPLOYEE} — 직원/관리자만</li>
 *   <li>{@link #ADMIN}    — 관리자만</li>
 * </ul>
 *
 * <p>현재 폼 필드({@code BbsMasterSaveForm.readAuth}) 는 form binding 호환성 위해 String 유지.
 * 본 enum 은 (a) single source of truth (b) 정합성 가드 기준점 (c) Service 단 사용 시
 * 자동완성 + 컴파일러 검증 제공.
 *
 * <p>회귀 가드: {@code AuthEnumIntegrityTest} 가 본 enum 과
 * {@code BbsMasterSaveForm.readAuth @Pattern} 정규식의 양방향 정합 강제.
 */
public enum ReadAuth {
    ALL,
    MEMBER,
    EMPLOYEE,
    ADMIN;

    /** 안전 파싱 — null/blank/알 수 없는 값이면 {@link #ALL}. */
    public static ReadAuth safeParse(String raw) {
        if (raw == null || raw.isBlank()) return ALL;
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return ALL;
        }
    }
}
