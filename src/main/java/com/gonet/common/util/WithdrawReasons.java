package com.gonet.common.util;

import java.util.Set;

/**
 * 탈퇴/퇴사 사유 정규화 — {@code *_withdraw.withdraw_reason} 컬럼 저장 형식 보호.
 *
 * <p>해당 컬럼들은 {@code VARCHAR(50)} + (일부는) CHECK enum 제약이 있어,
 * 사용자 입력 자유 텍스트를 그대로 저장하면 다음 사고가 발생한다:
 * <ul>
 *   <li>Member: {@code CHECK IN ('USER_REQUEST','ADMIN_FORCE','DORMANT_EXPIRED')} 위반 → 즉시 실패</li>
 *   <li>Admin / Employee: 50자 초과 silent truncation — 감사 추적성 손실</li>
 * </ul>
 *
 * <p>해결: 각 도메인 서비스가 본 유틸로 입력을 정규화해
 * <b>DB 에는 카테고리 코드</b>만 저장하고, 자유 텍스트 원문은
 * 감사 로그({@code log_audit.after_value}) 에만 보관한다 (법적 조사 추적성 유지).
 */
public final class WithdrawReasons {

    private WithdrawReasons() {}

    /**
     * 정규화 결과.
     *
     * @param category DB {@code withdraw_reason} 컬럼에 저장할 카테고리 코드 (입력값이 허용 집합 내면 그 값, 아니면 default)
     * @param note     자유 텍스트 원문 (null = 원문 없음). 감사 로그 전용.
     */
    public record Normalized(String category, String note) {}

    /**
     * {@code input} 이 {@code allowedCategories} 중 하나면 그대로 category 로 채택, note=null.
     * 그 외 (자유 텍스트·빈값·null) 면 {@code defaultCategory} 로 category 설정, 원문은 note 로 분리.
     *
     * @param input              서비스 layer 가 받은 reason 파라미터 (카테고리 코드이거나 자유 텍스트)
     * @param allowedCategories  도메인별 허용 카테고리 집합 (예: {@code Set.of("USER_REQUEST","ADMIN_FORCE")})
     * @param defaultCategory    입력이 카테고리가 아닐 때 사용할 기본값 — 반드시 {@code allowedCategories} 에 포함될 것
     */
    public static Normalized normalize(String input, Set<String> allowedCategories, String defaultCategory) {
        if (allowedCategories == null || allowedCategories.isEmpty()) {
            throw new IllegalArgumentException("allowedCategories must be non-empty");
        }
        if (defaultCategory == null || !allowedCategories.contains(defaultCategory)) {
            throw new IllegalArgumentException("defaultCategory must be one of allowedCategories");
        }
        if (input == null) return new Normalized(defaultCategory, null);
        String trimmed = input.trim();
        if (trimmed.isEmpty()) return new Normalized(defaultCategory, null);
        if (allowedCategories.contains(trimmed)) return new Normalized(trimmed, null);
        return new Normalized(defaultCategory, trimmed);
    }
}
