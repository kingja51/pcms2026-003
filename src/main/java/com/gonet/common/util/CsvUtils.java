package com.gonet.common.util;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * CSV 파싱/직렬화 공통 유틸.
 *
 * <p>주 용도:
 * <ul>
 *   <li>role_ids / group_ids (로그인 VIEW 에서 반환하는 UUID CSV)</li>
 *   <li>tb_role_url_access.required_roles / allowed_user_types</li>
 *   <li>공통코드 다중 선택 컬럼 (code_values)</li>
 * </ul>
 *
 * <p>입력 trim·공백 토큰 제거·빈 입력 방어 처리. 중복 제거는 {@link #toSet(String)}.
 */
public final class CsvUtils {

    private CsvUtils() {}

    /**
     * CSV → List&lt;String&gt; (순서 유지, 공백 제거, 빈 토큰 제외, 중복 허용).
     */
    public static List<String> toList(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return java.util.Arrays.stream(csv.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toList());
    }

    /**
     * CSV → Set&lt;String&gt; (순서 유지, 중복 제거).
     */
    public static Set<String> toSet(String csv) {
        if (csv == null || csv.isBlank()) return Collections.emptySet();
        Set<String> out = new LinkedHashSet<>();
        for (String s : csv.split(",")) {
            String t = s.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    /**
     * 두 CSV 중 하나라도 토큰이 겹치면 true. 교집합 여부만 판정(값 반환 없음).
     * {@code null}/빈값 은 false.
     */
    public static boolean hasAnyMatch(String csvA, String csvB) {
        if (csvA == null || csvA.isBlank() || csvB == null || csvB.isBlank()) return false;
        Set<String> a = toSet(csvA);
        for (String t : toSet(csvB)) {
            if (a.contains(t)) return true;
        }
        return false;
    }

    /**
     * List&lt;String&gt; → CSV (null/빈 토큰 제외).
     */
    public static String join(List<String> values) {
        if (values == null || values.isEmpty()) return "";
        return values.stream()
            .filter(s -> s != null && !s.isBlank())
            .map(String::trim)
            .collect(Collectors.joining(","));
    }
}
