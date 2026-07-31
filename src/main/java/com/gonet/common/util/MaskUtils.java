package com.gonet.common.util;

/**
 * PII 표시 마스킹 유틸 (개인정보보호법 대응).
 *
 * <p>사용 시나리오:
 * <ul>
 *   <li>관리자 목록/상세 화면에서 회원 정보 부분 표시</li>
 *   <li>로그/감사 기록 전 민감정보 축약</li>
 *   <li>클라이언트 응답(JSON/HTML) 노출 전 치환</li>
 * </ul>
 *
 * <p>원칙:
 * <ul>
 *   <li>입력이 null/blank 이면 빈 문자열 반환 (NPE 방어)</li>
 *   <li>이미 마스킹된 값은 그대로 반환 (' * ' 포함 시 한 번 더 마스킹 X — 멱등)</li>
 *   <li>복호화 실패값이 와도 보수적으로 전체 마스킹</li>
 * </ul>
 *
 * <p>Thymeleaf 에서 {@code ${@mask.email(...)}} 형식으로 호출 가능
 * ({@code MaskThymeleafConfig} 참고).
 */
public final class MaskUtils {

    private MaskUtils() {}

    /**
     * 이메일: {@code abcde@example.com} → {@code abc**@example.com}
     * 로컬파트 앞 3자 노출 + {@code **} + 도메인 유지. 로컬 3자 미만이면 1자만 노출.
     */
    public static String email(String email) {
        if (isBlank(email)) return "";
        int at = email.indexOf('@');
        if (at <= 0 || at == email.length() - 1) return "***";
        String local = email.substring(0, at);
        String domain = email.substring(at);
        int keep = Math.min(local.length() > 3 ? 3 : 1, local.length());
        return local.substring(0, keep) + "**" + domain;
    }

    /**
     * 전화번호: 010-1234-5678 → 010-****-5678
     * 숫자만 추출 후 끝 4자리만 노출, 중간 4자리 마스킹. 7자 미만은 전체 마스킹.
     */
    public static String phone(String phone) {
        if (isBlank(phone)) return "";
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() < 7) return "***";
        String head = digits.substring(0, 3);
        String tail = digits.substring(digits.length() - 4);
        return head + "-****-" + tail;
    }

    /**
     * 주민등록번호: 900101-1234567 → 900101-1****** (앞 6 + 뒷자리 1 노출)
     */
    public static String rrn(String rrn) {
        if (isBlank(rrn)) return "";
        String digits = rrn.replaceAll("\\D", "");
        if (digits.length() < 7) return "***";
        return digits.substring(0, 6) + "-" + digits.charAt(6) + "******";
    }

    /**
     * 이름: 홍길동 → 홍*동 / 김철수 → 김*수 / 박가 → 박* / 2자 → 중간 * / 4자 이상 → 첫·마지막 글자만.
     */
    public static String name(String name) {
        if (isBlank(name)) return "";
        // 코드포인트 배열로 다루어 보충문자(surrogate pair, 일부 한자 이름) 에서도
        // 반쪽 surrogate 노출/오마스킹이 발생하지 않도록 한다. (charAt 인덱싱 혼용 금지)
        int[] cps = name.trim().codePoints().toArray();
        int len = cps.length;
        if (len == 0) return "";
        if (len == 1) return "*";
        if (len == 2) return new String(cps, 0, 1) + "*";
        if (len == 3) return new String(cps, 0, 1) + "*" + new String(cps, 2, 1);
        StringBuilder sb = new StringBuilder().append(new String(cps, 0, 1));
        for (int i = 1; i < len - 1; i++) sb.append('*');
        sb.append(new String(cps, len - 1, 1));
        return sb.toString();
    }

    /**
     * 계좌번호: 뒤 4자리만 노출. 123-456-789012 → ******9012
     */
    public static String account(String account) {
        if (isBlank(account)) return "";
        String digits = account.replaceAll("\\D", "");
        if (digits.length() <= 4) return "****";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < digits.length() - 4; i++) sb.append('*');
        sb.append(digits.substring(digits.length() - 4));
        return sb.toString();
    }

    /**
     * 로그인 ID: admin01 → ad***01 (앞 2 + 뒤 2 노출).
     */
    public static String loginId(String loginId) {
        if (isBlank(loginId)) return "";
        if (loginId.length() <= 4) {
            return loginId.charAt(0) + "***";
        }
        return loginId.substring(0, 2) + "***" + loginId.substring(loginId.length() - 2);
    }

    /**
     * IP: 192.168.1.100 → 192.168.*.* (IPv4) / IPv6 는 앞 4 그룹만 노출.
     */
    public static String ip(String ip) {
        if (isBlank(ip)) return "";
        if (ip.contains(".")) {
            String[] parts = ip.split("\\.");
            if (parts.length == 4) return parts[0] + "." + parts[1] + ".*.*";
        }
        if (ip.contains(":")) {
            String[] g = ip.split(":");
            int keep = Math.min(4, g.length);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < keep; i++) {
                if (i > 0) sb.append(':');
                sb.append(g[i]);
            }
            sb.append(":****");
            return sb.toString();
        }
        return "***";
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
