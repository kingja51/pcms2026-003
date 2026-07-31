package com.gonet.common.crypto;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * PII 필드 표시 어노테이션.
 *
 * <p>MyBatis {@code EncryptInterceptor} 는 이 어노테이션이 붙은 필드를:
 * <ul>
 *   <li>DB 저장 전 → AES-256-GCM 암호화 (결과 {@code {AG}...})</li>
 *   <li>DB 조회 후 → 프리픽스 판별 후 복호화 (평문은 그대로 반환, legacy 호환)</li>
 * </ul>
 *
 * <p>적용 컬럼 예: 회원 이름·이메일·전화번호·주민번호 등 VARCHAR(512).
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Encrypt {
}
