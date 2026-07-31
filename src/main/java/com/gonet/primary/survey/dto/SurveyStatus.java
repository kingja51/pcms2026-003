package com.gonet.primary.survey.dto;

import java.util.Locale;

/**
 * tb_survey.status 도메인 enum.
 *
 * <ul>
 *   <li>{@link #DRAFT}     : 작성 중 — 사용자 미공개</li>
 *   <li>{@link #PUBLISHED} : 공개 — 기간 내 응답 가능</li>
 *   <li>{@link #CLOSED}    : 종료 — 결과만 노출, 응답 불가</li>
 * </ul>
 */
public enum SurveyStatus {
    DRAFT, PUBLISHED, CLOSED;

    public static SurveyStatus safeParse(String raw) {
        if (raw == null || raw.isBlank()) return DRAFT;
        try {
            return SurveyStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return DRAFT;
        }
    }
}
