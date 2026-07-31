package com.gonet.common.audit;

import com.gonet.logging.privacy.dto.PrivacyAccessLog;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 개인정보 접근 메서드 마킹 — {@link PrivacyAccessAspect} 가
 * 메서드 진입/종료를 감싸 {@code log_privacy_access} INSERT 를 자동 발행.
 *
 * <p>대상 — {@code @Encrypt} 필드를 다루는 모든 도메인의 사용자 대면 경계:
 * <ul>
 *   <li>관리자 list/search/excel — {@code action = SEARCH | EXPORT}</li>
 *   <li>관리자 단건 조회/수정/삭제 — {@code action = READ | UPDATE | DELETE}</li>
 *   <li>회원 본인 마이페이지 조회/수정 — 본인 PII 처리도 PIPA §8 대상</li>
 * </ul>
 *
 * <p>예시:
 * <pre>
 * @PrivacyAccess(
 *     action = PrivacyAccessLog.ACTION_EXPORT,
 *     entity = "tb_member",
 *     fields = "name,email,phone",
 *     reasonParam = "excelReq.downloadReason"
 * )
 * public void exportExcel(...) { ... }
 * </pre>
 *
 * <p>{@code reasonParam} / {@code targetIdParam} 는 SpEL 형태 (PrivacyAccessAspect 가 평가).
 * 현재 단순화 — 향후 SpEL 지원 시 확장.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PrivacyAccess {

    /** 수행업무 — {@link PrivacyAccessLog} 의 ACTION_* 상수 사용 권장 */
    String action() default PrivacyAccessLog.ACTION_READ;

    /** 대상 테이블 — tb_member / tb_admin / tb_employee / ... */
    String entity();

    /** 취급 PII 항목 CSV — name,email,phone,rrn,... */
    String fields() default "";

    /** 정보주체 user_type 힌트 — 다중 도메인일 경우 미지정 */
    String targetUserType() default "";
}
