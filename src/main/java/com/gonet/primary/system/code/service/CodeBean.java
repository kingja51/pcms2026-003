package com.gonet.primary.system.code.service;

import com.gonet.primary.system.code.dto.CodeOption;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Thymeleaf 표현식에서 {@code ${@code.label(...)}} / {@code ${@code.options(...)}}
 * 형태로 호출하기 위한 경량 래퍼 빈. CodeService 의 캐시된 메서드를 그대로 위임.
 *
 * <p>사용 예 (Thymeleaf):
 * <pre>
 *   &lt;!-- 라벨 --&gt;
 *   &lt;span th:text="${@code.label('USER_STATUS', member.status)}"&gt;활성&lt;/span&gt;
 *
 *   &lt;!-- 드롭다운 --&gt;
 *   &lt;select th:name="status"&gt;
 *     &lt;option th:each="o : ${@code.options('USER_STATUS')}"
 *             th:value="${o.code}" th:text="${o.codeName}"&gt;&lt;/option&gt;
 *   &lt;/select&gt;
 * </pre>
 *
 * <p>Bean 이름은 {@code "code"} 로 고정 — {@code @code} 접근을 위해.
 */
@Component("code")
public class CodeBean {

    private final CodeService service;

    public CodeBean(CodeService service) {
        this.service = service;
    }

    /** 코드 → 코드명 변환. 없으면 원래 code 반환 (템플릿에서 fallback 자연스럽게). */
    public String label(String groupCode, String code) {
        if (code == null) return "";
        String name = service.label(groupCode, code);
        return name != null ? name : code;
    }

    /** 그룹 코드 → 활성 코드 옵션 리스트 (sort_order 정렬). */
    public List<CodeOption> options(String groupCode) {
        return service.options(groupCode);
    }
}
