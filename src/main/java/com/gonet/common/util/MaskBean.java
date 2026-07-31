package com.gonet.common.util;

import org.springframework.stereotype.Component;

/**
 * Thymeleaf 표현식에서 {@code ${@mask.email(...)}} 형태로 호출하기 위한 빈 래퍼.
 *
 * <p>정적 {@link MaskUtils} 를 Thymeleaf SpEL 이 접근할 수 있도록 Spring Bean 으로 노출.
 * 빈 이름은 소문자 {@code "mask"}.
 *
 * <p>Thymeleaf 사용 예:
 * <pre>
 *   &lt;td th:text="${@mask.email(member.email)}"&gt;a**@x.com&lt;/td&gt;
 *   &lt;td th:text="${@mask.phone(member.phone)}"&gt;010-****-5678&lt;/td&gt;
 *   &lt;td th:text="${@mask.name(member.name)}"&gt;홍*동&lt;/td&gt;
 *   &lt;td th:text="${@mask.loginId(admin.loginId)}"&gt;ad***01&lt;/td&gt;
 * </pre>
 */
@Component("mask")
public class MaskBean {

    public String email(String v)    { return MaskUtils.email(v); }
    public String phone(String v)    { return MaskUtils.phone(v); }
    public String rrn(String v)      { return MaskUtils.rrn(v); }
    public String name(String v)     { return MaskUtils.name(v); }
    public String account(String v)  { return MaskUtils.account(v); }
    public String loginId(String v)  { return MaskUtils.loginId(v); }
    public String ip(String v)       { return MaskUtils.ip(v); }
}
