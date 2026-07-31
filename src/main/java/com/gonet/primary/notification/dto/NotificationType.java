package com.gonet.primary.notification.dto;

/**
 * 알림 타입 enum — DB 에는 문자열로 저장.
 *
 * <p>Phase 1 트리거: BOARD_REPORT / BOARD_COMMENT / SURVEY_RESPONSE / SYSTEM.
 * Phase 2 후속: DORMANT_WARN / FILE_INFECTED / MEMBER_JOINED 등.
 */
public enum NotificationType {

    /** 게시글이 신고 임계 도달로 자동 REPORTED 전환 — 작성자 대상. */
    BOARD_REPORT("게시글 신고 누적"),

    /** 자기 글/댓글에 새 댓글이 달림 — 원글/부모 댓글 작성자 대상. */
    BOARD_COMMENT("새 댓글"),

    /** 자기가 만든 설문에 응답이 도착 — 설문 마스터 owner 대상. */
    SURVEY_RESPONSE("설문 응답 도착"),

    /** 회원 휴면 사전 안내 — Phase 2. */
    DORMANT_WARN("휴면 안내"),

    /** 업로드 파일 INFECTED 검출 — 업로더/관리자 대상. Phase 2. */
    FILE_INFECTED("파일 바이러스 감지"),

    /** 관리자가 수동 발송한 시스템 공지/안내. */
    SYSTEM("시스템 알림");

    private final String label;

    NotificationType(String label) { this.label = label; }

    public String label() { return label; }

    public static NotificationType safeParse(String s) {
        if (s == null) return SYSTEM;
        for (NotificationType t : values()) {
            if (t.name().equalsIgnoreCase(s)) return t;
        }
        return SYSTEM;
    }
}
