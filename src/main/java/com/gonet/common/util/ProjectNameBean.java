package com.gonet.common.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 프로젝트명 노출 빈 — Thymeleaf 표현식 {@code ${@project.nameKo}} / {@code ${@project.nameEn}}.
 *
 * <p>application.yml 의 {@code gopcms.project.name-ko} / {@code gopcms.project.name-en}
 * 를 단일 진입점으로 노출. 환경별 yml 또는 환경변수
 * {@code PCMS_PROJECT_NAME_KO} / {@code PCMS_PROJECT_NAME_EN} 으로 오버라이드.
 *
 * <p>사용 예:
 * <pre>
 *   &lt;title&gt;[[${&#64;project.nameKo}]] 관리자&lt;/title&gt;
 *   &lt;span th:text="${&#64;project.nameEn}"&gt;GoPCMS&lt;/span&gt;
 * </pre>
 */
@Component("project")
public class ProjectNameBean {

    private final String nameKo;
    private final String nameEn;

    public ProjectNameBean(@Value("${gopcms.project.name-ko:지오넷 PCMS 5.0}") String nameKo,
                            @Value("${gopcms.project.name-en:GoPCMS}") String nameEn) {
        this.nameKo = nameKo;
        this.nameEn = nameEn;
    }

    /** 한글 프로젝트명 (UI 노출용). */
    public String getNameKo() { return nameKo; }
    public String nameKo()    { return nameKo; }   // Thymeleaf {@code ${@project.nameKo}} 호환

    /** 영문 프로젝트명 (sitemap/robots/X-Powered-By 등 기술 영역). */
    public String getNameEn() { return nameEn; }
    public String nameEn()    { return nameEn; }
}
