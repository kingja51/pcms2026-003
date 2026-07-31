package com.gonet.common.util;

/**
 * 감사 로그 {@code beforeJson/afterJson} 필드에 간단한 문자열·숫자만 담을 때 사용하는
 * 경량 JSON 이스케이프 유틸. 복잡한 객체 직렬화는 Jackson 사용 권장.
 */
public final class JsonUtils {

    private JsonUtils() {}

    /**
     * file-picker 가 전송하는 hidden input value (JSON UUID 배열) 의 첫 원소를 반환.
     * 예: {@code ["019d-..."]} → {@code "019d-..."}.
     * 형식이 잘못되었거나 빈 배열이면 {@code null}.
     *
     * <p>Jackson 미사용 manual parser — 입력 견고성을 우선.
     */
    public static String firstUuidFromJsonArray(String json) {
        if (json == null || json.isBlank()) return null;
        String t = json.trim();
        if (t.equals("[]") || t.equals("null")) return null;
        if (t.startsWith("[")) t = t.substring(1);
        if (t.endsWith("]"))   t = t.substring(0, t.length() - 1);
        for (String raw : t.split(",")) {
            String s = raw.trim();
            if (s.isEmpty()) continue;
            if ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'"))) {
                s = s.substring(1, s.length() - 1);
            }
            // 36자 순수 UUID (레거시) 또는 "PFX_<UUID>" prefix 형태(40자) 허용
            if (s.matches("([A-Z0-9]{3}_)?[0-9a-fA-F-]{36}")) return s;
        }
        return null;
    }

    /** 문자열을 JSON 문자열 리터럴로 포장. null 은 {@code null} 리터럴 반환. */
    public static String quote(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder(s.length() + 2).append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"'  -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.append('"').toString();
    }
}
