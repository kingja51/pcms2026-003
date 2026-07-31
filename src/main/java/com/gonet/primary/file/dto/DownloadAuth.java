package com.gonet.primary.file.dto;

/**
 * 파일 그룹 다운로드 권한 카탈로그 — {@code tb_file_group.download_auth} 와 동기화.
 *
 * <p>본 enum 은 BBS / 콘텐츠 / 일반 파일 등 모든 file_group 도메인에서 공용. 의미
 * (FileServiceImpl.enforceDownloadAuth / enforceOwnerPrivacy 와 정합):
 *
 * <ul>
 *   <li>{@link #ANONYMOUS}       — 비로그인 포함 누구나 (썸네일/공개 콘텐츠)</li>
 *   <li>{@link #ROLE_MEMBER}     — 로그인 사용자 (기본값)</li>
 *   <li>{@link #ROLE_EMPLOYEE}   — 직원/관리자</li>
 *   <li>{@link #ROLE_STAFF}      — 운영 STAFF 그룹</li>
 *   <li>{@link #OWNER_PRIVACY}   — 본인(BBS created_by) + ROLE_PRIVACY 만 (ROLE_ADMIN 자동 통과 금지)</li>
 *   <li>{@link #ROLE_MANAGER}    — 관리 매니저</li>
 *   <li>{@link #ROLE_ADMIN}      — 관리자만</li>
 * </ul>
 *
 * <p>{@code OWNER_PRIVACY} 는 특수 정책 — 다른 정책 평가보다 먼저 처리되며 ROLE_ADMIN
 * 도 통과시키지 않는다 (개인정보 영역 가드). 운영 시 신중히 사용.
 *
 * <p>{@code BbsMasterSaveForm.downloadAuth} / {@code FileGroup.downloadAuth} 필드는
 * form binding 호환성 + DB 컬럼 호환성 위해 String 유지. 본 enum 은 single source of truth.
 *
 * <p>회귀 가드: {@code AuthEnumIntegrityTest} 가 본 enum 과
 * {@code BbsMasterSaveForm.downloadAuth @Pattern} 정규식의 양방향 정합 강제.
 */
public enum DownloadAuth {
    ANONYMOUS,
    ROLE_MEMBER,
    ROLE_EMPLOYEE,
    ROLE_STAFF,
    OWNER_PRIVACY,
    ROLE_MANAGER,
    ROLE_ADMIN;

    /** 안전 파싱 — null/blank/알 수 없는 값이면 {@link #ROLE_MEMBER} (가장 안전한 기본값). */
    public static DownloadAuth safeParse(String raw) {
        if (raw == null || raw.isBlank()) return ROLE_MEMBER;
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return ROLE_MEMBER;
        }
    }
}
