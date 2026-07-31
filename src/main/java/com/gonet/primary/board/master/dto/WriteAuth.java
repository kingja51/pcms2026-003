package com.gonet.primary.board.master.dto;

/**
 * 게시판 쓰기 권한 카탈로그 — {@code tb_bbs_master.write_auth} 와 동기화.
 *
 * <p>의미 (BoardArticleServiceImpl.ensureCanWrite 와 정합):
 * <ul>
 *   <li>{@link #MEMBER}   — 로그인 회원 (회원/직원/관리자 모두 통과)</li>
 *   <li>{@link #EMPLOYEE} — 직원/관리자만</li>
 *   <li>{@link #ADMIN}    — 관리자만</li>
 * </ul>
 *
 * <p>{@code GUEST} (비로그인 작성) 은 §0.18 에서 정책적으로 폐기 — 본 enum 에 미포함.
 * 향후 비로그인 작성이 다시 필요해지면 {@code GUEST} 를 추가하고
 * {@code BbsMasterSaveForm.writeAuth @Pattern} 정규식 + Service 처리 분기 동시 갱신
 * (AuthEnumIntegrityTest 가 빌드 단계 강제).
 *
 * <p>현재 폼 필드({@code BbsMasterSaveForm.writeAuth}) 는 form binding 호환성 위해 String 유지.
 */
public enum WriteAuth {
    MEMBER,
    EMPLOYEE,
    ADMIN;

    /** 안전 파싱 — null/blank/알 수 없는 값이면 {@link #MEMBER}. */
    public static WriteAuth safeParse(String raw) {
        if (raw == null || raw.isBlank()) return MEMBER;
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return MEMBER;
        }
    }
}
