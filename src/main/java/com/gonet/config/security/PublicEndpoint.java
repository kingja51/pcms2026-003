package com.gonet.config.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 공개(PERMIT_ALL) 엔드포인트 마커.
 *
 * <p>컨트롤러 클래스(또는 개별 핸들러 메서드)에 부착하면, 해당 URL 들은
 * {@code tb_role_url_access} 등록·{@code SecurityConfig} 수정 없이 누구나(로그인·비로그인 무관) 접근 가능.
 *
 * <p>{@link PublicEndpointRegistry} 가 기동 시 본 어노테이션이 달린 핸들러의 URL 패턴을 수집하고,
 * {@code DynamicAuthorizationManager} 가 DB 규칙 평가에 앞서 먼저 매칭하여 GRANT 한다.
 *
 * <p><b>secure-by-default 유지</b>: 본 어노테이션은 명시적 opt-in 이다.
 * 부착하지 않은 컨트롤러는 기존대로 {@code tb_role_url_access} fallback DENY 로 보호된다 —
 * 즉 인증이 필요한 화면(마이페이지 등)에서 어노테이션을 깜빡해도 노출되지 않는다.
 *
 * <p>사용 예 — 사용자 대면 공개 페이지 컨트롤러:
 * <pre>
 * &#64;PublicEndpoint
 * &#64;Controller
 * &#64;RequestMapping("/contract/{siteCode}")
 * public class ContractUsrController { ... }
 * </pre>
 *
 * <p><b>주의</b>: 인증·인가가 필요한 컨트롤러(MngController, 마이페이지 등)에는 절대 부착하지 말 것.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PublicEndpoint {
}
