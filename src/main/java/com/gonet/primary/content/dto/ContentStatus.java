package com.gonet.primary.content.dto;

/**
 * 콘텐츠 게시 상태 전환.
 *
 * <p>전이 규칙:
 * <pre>
 *   DRAFT ─(submit)─▶ REVIEW ─(approve)─▶ APPROVED ─(publish)─▶ PUBLISHED ─(unpublish)─▶ UNPUBLISHED
 *                       │                      │                                             │
 *                       └─(reject)─▶ DRAFT     └─(reject)─▶ DRAFT                            └─(republish)─▶ APPROVED
 * </pre>
 *
 * <p>PUBLISHED 는 {@code tb_content.published_at} 자동 세팅. UNPUBLISHED 는 {@code unpublish_at} 세팅.
 */
public enum ContentStatus {

    DRAFT,
    REVIEW,
    APPROVED,
    PUBLISHED,
    UNPUBLISHED;

    public boolean isEditable() {
        return this == DRAFT;
    }

    public boolean isPubliclyVisible() {
        return this == PUBLISHED;
    }

    public static ContentStatus safeParse(String raw) {
        if (raw == null || raw.isBlank()) return DRAFT;
        try {
            return ContentStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return DRAFT;
        }
    }

    /**
     * 허용된 전이 검증.
     *
     * @throws IllegalStateException 금지된 전이 시
     */
    public void assertCanTransitionTo(ContentStatus target) {
        if (!canTransitionTo(target)) {
            throw new IllegalStateException(
                "금지된 상태 전이: " + this + " → " + target);
        }
    }

    public boolean canTransitionTo(ContentStatus target) {
        if (target == null || target == this) return false;
        return switch (this) {
            case DRAFT        -> target == REVIEW;
            case REVIEW       -> target == APPROVED || target == DRAFT;
            case APPROVED     -> target == PUBLISHED || target == DRAFT;
            case PUBLISHED    -> target == UNPUBLISHED;
            case UNPUBLISHED  -> target == APPROVED || target == PUBLISHED;
        };
    }
}
