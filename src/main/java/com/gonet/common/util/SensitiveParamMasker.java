package com.gonet.common.util;

import java.util.Locale;
import java.util.Set;

/**
 * 쿼리스트링·URL 의 비밀값 파라미터를 일괄 마스킹.
 *
 * <p>책임: GET 파라미터 형태로 들어온 비밀번호 reset 토큰 · NICE CI/DI · 인증코드 ·
 * API key 등이 log_access / log_error 의 query_string 컬럼에 평문으로 적재되어
 * 12개월 retention 동안 누적되는 회귀 차단.
 *
 * <p>정책:
 * <ul>
 *   <li>파라미터 이름이 화이트리스트에 매칭되면 값을 {@code ***} 로 치환</li>
 *   <li>대소문자 무관(case-insensitive) 매칭</li>
 *   <li>매칭 안 되는 파라미터는 그대로 보존 — 통계·디버깅 가치 유지</li>
 *   <li>빈 입력/null 은 그대로 통과</li>
 * </ul>
 *
 * <p>호출처:
 * <ul>
 *   <li>{@code AccessLogSnapshot.capture()} — log_access.query_string 적재 직전</li>
 *   <li>{@code ErrorLoggerImpl.populateRequest()} — log_error.query_string 적재 직전</li>
 * </ul>
 */
public final class SensitiveParamMasker {

    private static final Set<String> SENSITIVE_KEYS = Set.of(
        // 인증·세션
        "password", "passwd", "pwd", "newpassword", "newpasswd", "currentpassword",
        "token", "accesstoken", "access_token", "refreshtoken", "refresh_token",
        "id_token", "idtoken", "authcode", "auth_code", "code",
        "secret", "client_secret", "clientsecret",
        "auth", "authorization",
        // API key
        "apikey", "api_key", "accesskey", "access_key", "key",
        // 본인인증
        "ci", "di", "encdata", "enc_data", "encodedata", "encode_data",
        "verifytoken", "verify_token", "verify", "otp", "totp",
        // 비밀번호 재설정
        "resettoken", "reset_token", "resetkey", "reset_key",
        // OAuth2
        "state", "nonce",
        // 기타
        "signature", "sig"
    );

    private static final String MASK = "***";

    private SensitiveParamMasker() {}

    /**
     * query string 의 모든 비밀값 파라미터를 마스킹. URL-decode 하지 않는다(왕복 안전).
     *
     * @param queryString 원본 query string (예: {@code "q=hello&token=ABC&page=1"})
     * @return 마스킹된 query string. null 입력 시 null.
     */
    public static String mask(String queryString) {
        if (queryString == null || queryString.isEmpty()) return queryString;

        StringBuilder sb = new StringBuilder(queryString.length());
        int start = 0;
        final int len = queryString.length();

        for (int i = 0; i <= len; i++) {
            if (i == len || queryString.charAt(i) == '&') {
                if (i > start) {
                    appendMasked(sb, queryString, start, i);
                } else if (i < len) {
                    // 연속된 '&' 또는 시작이 '&' 인 경우 — 빈 토큰
                }
                if (i < len) sb.append('&');
                start = i + 1;
            }
        }
        return sb.toString();
    }

    private static void appendMasked(StringBuilder sb, String qs, int start, int end) {
        int eq = -1;
        for (int i = start; i < end; i++) {
            if (qs.charAt(i) == '=') { eq = i; break; }
        }
        if (eq < 0) {
            // 키만 있고 = 없음
            sb.append(qs, start, end);
            return;
        }
        String name = qs.substring(start, eq);
        sb.append(name).append('=');
        if (eq + 1 >= end) return; // 빈 값
        if (SENSITIVE_KEYS.contains(name.toLowerCase(Locale.ROOT))) {
            sb.append(MASK);
        } else {
            sb.append(qs, eq + 1, end);
        }
    }
}
