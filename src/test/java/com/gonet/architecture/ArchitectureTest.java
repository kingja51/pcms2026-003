package com.gonet.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.egovframe.rte.fdl.cmmn.EgovAbstractServiceImpl;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * PCMS 2026-003 아키텍처 규약 게이트 — 개발가이드 §4-4.
 *
 * <p>이 프로젝트는 단위 테스트가 얇다. 본 게이트와 §15 의 grep 검사가 자동 검증의 축이다.
 *
 * <p><b>예외를 늘리지 않는다.</b> 001 은 R3 에 실측 예외 2건(모니터링 서비스, Gemini 캐시)을
 * 두었으나 003 에는 해당 클래스가 없다. eGov 호환성 규칙 4 는 "예외 없음"이므로,
 * 상속이 곤란한 기술 서비스는 예외를 추가하지 말고 {@code EgovAbstractServiceImpl} 을 상속한
 * 공통 추상 서비스를 경유시킨다(개발가이드 §7-1).
 *
 * <p>실측으로 인정하는 의존:
 * <ul>
 *   <li>{@code common/audit} → {@code logging} — 감사 기록기가 logging DB 매퍼를 쓴다</li>
 *   <li>{@code logging} → {@code primary} 는 <b>dto 한정</b> — {@code CustomUserDetails} 등</li>
 *   <li>{@code primary} → {@code logging} — 감사 다중 경로(도메인이 기록기를 직접 호출)</li>
 * </ul>
 */
@AnalyzeClasses(packages = "com.gonet", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    /**
     * R1. 컨트롤러 접미사 — 도메인 패키지의 컨트롤러는 Api/Usr/Mng 3종만.
     * {@code config} 패키지의 기술 컨트롤러({@code CspReportController} 등)는 대상 외.
     */
    @ArchTest
    static final ArchRule r1_controller_suffix =
        classes().that().resideInAnyPackage("com.gonet.primary..", "com.gonet.secondary..", "com.gonet.logging..")
            .and().areAnnotatedWith(Controller.class)
            .or().resideInAnyPackage("com.gonet.primary..", "com.gonet.secondary..", "com.gonet.logging..")
            .and().areAnnotatedWith(RestController.class)
            .should().haveSimpleNameEndingWith("ApiController")
            .orShould().haveSimpleNameEndingWith("UsrController")
            .orShould().haveSimpleNameEndingWith("MngController")
            .allowEmptyShould(true);

    /**
     * R2. Controller 는 Mapper 를 직접 참조하지 않는다 (Service 경유).
     * 호환성 규칙 3 — "Controller 가 DAO 메서드를 호출할 수 없다" 와 같은 취지다.
     *
     * <p>예외: {@code system.login.controller} 의 로그인 Success/Failure 핸들러.
     * 로그인 잠금 카운터는 인증 경로라 Service 트랜잭션 밖에서 매퍼를 직행한다(001 실측).
     */
    @ArchTest
    static final ArchRule r2_controller_no_mapper =
        noClasses().that().resideInAPackage("..controller..")
            .and().resideOutsideOfPackage("..system.login.controller..")
            .should().dependOnClassesThat().resideInAPackage("..mapper..")
            .allowEmptyShould(true);

    /**
     * R3. {@code *ServiceImpl} 은 {@link EgovAbstractServiceImpl} 상속 — 직접·간접 모두 인정.
     * <b>호환성 규칙 4 는 예외가 없다.</b> 예외를 추가하지 말 것.
     */
    @ArchTest
    static final ArchRule r3_serviceimpl_egov =
        classes().that().haveSimpleNameEndingWith("ServiceImpl")
            .and().resideInAnyPackage("com.gonet.primary..", "com.gonet.secondary..")
            .should().beAssignableTo(EgovAbstractServiceImpl.class)
            .allowEmptyShould(true);

    /**
     * R4-a. {@link EgovMapper} 는 인터페이스이며 {@code ..mapper..} 패키지에만 위치.
     * 호환성 규칙 5 — 매퍼 스캔은 {@code MapperConfigurer} 가 담당한다.
     */
    @ArchTest
    static final ArchRule r4a_mapper_location =
        classes().that().areAnnotatedWith(EgovMapper.class)
            .should().beInterfaces()
            .andShould().resideInAPackage("..mapper..")
            .allowEmptyShould(true);

    /**
     * R4-b. MyBatis 의 {@code @Mapper} 사용 0건.
     * {@code @Mapper} 는 실행환경 v4.3 이하 표기라 5.0 기준 <b>호환성 위반</b>이다.
     * 001 은 이 방식이었으므로 이식할 때 반드시 {@link EgovMapper} 로 바꾼다.
     */
    @ArchTest
    static final ArchRule r4b_no_mybatis_mapper =
        noClasses().that().resideInAPackage("com.gonet..")
            .should().beAnnotatedWith(org.apache.ibatis.annotations.Mapper.class)
            .allowEmptyShould(true);

    /** R5-a. primary 는 secondary 에 의존 금지. (primary → logging 은 감사 다중 경로로 허용) */
    @ArchTest
    static final ArchRule r5a_primary_isolation =
        noClasses().that().resideInAPackage("com.gonet.primary..")
            .should().dependOnClassesThat().resideInAnyPackage("com.gonet.secondary..")
            .allowEmptyShould(true);

    /** R5-b. secondary 는 primary/logging 에 의존 금지. */
    @ArchTest
    static final ArchRule r5b_secondary_isolation =
        noClasses().that().resideInAPackage("com.gonet.secondary..")
            .should().dependOnClassesThat().resideInAnyPackage("com.gonet.primary..", "com.gonet.logging..")
            .allowEmptyShould(true);

    /** R5-c. logging 은 secondary 금지, primary 는 {@code dto} 패키지만 허용. */
    @ArchTest
    static final ArchRule r5c_logging_isolation =
        noClasses().that().resideInAPackage("com.gonet.logging..")
            .should().dependOnClassesThat(
                DescribedPredicate.describe(
                    "secondary 전체 또는 primary 의 dto 외 패키지",
                    c -> c.getPackageName().startsWith("com.gonet.secondary")
                      || (c.getPackageName().startsWith("com.gonet.primary")
                          && !c.getPackageName().contains(".dto"))))
            .allowEmptyShould(true);

    /**
     * R6. {@code common} 독립성 — primary(dto 제외)/secondary/scheduler 의존 금지.
     *
     * <p>허용하는 의존 (개발가이드 §4-4 R6 실측 예외):
     * <ul>
     *   <li>{@code common/audit} → {@code logging} — 감사 기록기가 logging DB 매퍼를 쓴다</li>
     *   <li>{@code common} → {@code primary..dto} — DTO 는 계층 간 이동이 전제다</li>
     *   <li>{@code common/mail} → {@code primary.system.mail} — {@code MailService} 가
     *       DB 에 저장된 메일 템플릿({@code tb_mail_template})을 조회한다.
     *       템플릿은 운영자가 관리하는 도메인 데이터라 common 안에 둘 수 없다</li>
     * </ul>
     */
    @ArchTest
    static final ArchRule r6_common_independence =
        noClasses().that().resideInAPackage("com.gonet.common..")
            .should().dependOnClassesThat(
                DescribedPredicate.describe(
                    "primary(dto·system.mail 제외)/secondary/scheduler",
                    c -> c.getPackageName().startsWith("com.gonet.secondary")
                      || c.getPackageName().startsWith("com.gonet.scheduler")
                      || (c.getPackageName().startsWith("com.gonet.primary")
                          && !c.getPackageName().contains(".dto")
                          && !c.getPackageName().startsWith("com.gonet.primary.system.mail"))))
            .allowEmptyShould(true);

    /**
     * R7. <b>eGov 호환성 규칙 7</b> — 실행환경({@code org.egovframe.rte}) 클래스를 상속한 클래스는
     * 이름이 {@code Egov} 로 시작할 수 없고, {@code org.egovframe.rte} 패키지에 정의할 수도 없다.
     *
     * <p>우리 코드는 전부 {@code com.gonet} 이므로 패키지 조건은 자동 충족이다.
     * 001 의 {@code EgovXxx} 클래스를 그대로 복사해 오면 걸린다 — 이식 시 리네이밍한다.
     */
    @ArchTest
    static final ArchRule r7_no_egov_prefixed_class =
        noClasses().that().resideInAPackage("com.gonet..")
            .and().areAssignableTo(
                DescribedPredicate.describe(
                    "실행환경(org.egovframe.rte) 클래스",
                    c -> c.getPackageName().startsWith("org.egovframe.rte")))
            .should().haveSimpleNameStartingWith("Egov")
            .allowEmptyShould(true);
}
