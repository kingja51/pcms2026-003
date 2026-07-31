package com.gonet.primary.survey.dto;

import java.util.Locale;

/**
 * tb_survey_question.question_type 도메인 enum.
 *
 * <ul>
 *   <li>{@link #TEXT}     : 단답형 (input text)</li>
 *   <li>{@link #TEXTAREA} : 서술형 (textarea)</li>
 *   <li>{@link #RADIO}    : 단일 선택 (radio)</li>
 *   <li>{@link #CHECKBOX} : 복수 선택 (checkbox)</li>
 *   <li>{@link #SELECT}   : 단일 선택 (드롭다운)</li>
 *   <li>{@link #SCALE}    : 점수 (1~N 슬라이더)</li>
 * </ul>
 */
public enum QuestionType {
    TEXT, TEXTAREA, RADIO, CHECKBOX, SELECT, SCALE;

    public boolean hasOptions() {
        return this == RADIO || this == CHECKBOX || this == SELECT;
    }

    public boolean isMultiSelect() {
        return this == CHECKBOX;
    }

    public boolean isText() {
        return this == TEXT || this == TEXTAREA;
    }

    public static QuestionType safeParse(String raw) {
        if (raw == null || raw.isBlank()) return TEXT;
        try {
            return QuestionType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return TEXT;
        }
    }
}
