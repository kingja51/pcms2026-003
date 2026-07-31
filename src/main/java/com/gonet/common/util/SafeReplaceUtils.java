package com.gonet.common.util;

/**
 * NICE CheckPlus 샘플 JSP의 {@code requestReplace(...)} 메서드를 대체하는 공통 유틸.
 *
 * <p>외부에서 들어온 파라미터(특히 NICE 콜백의 {@code EncodeData})에서 SQL/HTML 위험
 * 문자를 제거한다. NICE 샘플의 정책을 그대로 옮기되, {@code "encodeData"} 분기에서는
 * Base64 문자({@code +} {@code /} {@code =}) 를 보존한다.
 *
 * <p>이 유틸은 의도적으로 가벼운 fail-safe 형태 — null 안전, 도메인 의존성 없음.
 */
public final class SafeReplaceUtils {

    private SafeReplaceUtils() {}

    /**
     * NICE 콜백 파라미터 정제. {@code gubun="encodeData"} 일 때만 Base64 문자를 보존.
     *
     * @param paramValue 원본 (null 허용 — null 이면 빈 문자열 반환)
     * @param gubun {@code "encodeData"} 또는 그 외 — Base64 문자 보존 여부 결정
     */
    public static String requestReplace(String paramValue, String gubun) {
        if (paramValue == null) return "";

        String v = paramValue
                .replace("<", "&lt;").replace(">", "&gt;")
                .replace("*", "").replace("?", "")
                .replace("[", "").replace("{", "")
                .replace("(", "").replace(")", "")
                .replace("^", "").replace("$", "")
                .replace("'", "").replace("@", "")
                .replace("%", "").replace(";", "")
                .replace(":", "").replace("-", "")
                .replace("#", "").replace(",", "");

        if (!"encodeData".equals(gubun)) {
            v = v.replace("+", "").replace("/", "").replace("=", "");
        }
        return v;
    }
}
