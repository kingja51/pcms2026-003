package com.gonet.config.egov;

import org.egovframe.rte.fdl.cmmn.trace.LeaveaTrace;
import org.egovframe.rte.fdl.cmmn.trace.handler.DefaultTraceHandler;
import org.egovframe.rte.fdl.cmmn.trace.handler.TraceHandler;
import org.egovframe.rte.fdl.cmmn.trace.manager.DefaultTraceHandleManager;
import org.egovframe.rte.fdl.cmmn.trace.manager.TraceHandlerService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.AntPathMatcher;

/**
 * eGovFramework RTE 5.0 공통 빈 등록.
 *
 * <p>{@link org.egovframe.rte.fdl.cmmn.EgovAbstractServiceImpl} 은 내부에서
 * {@code @Resource(name="leaveaTrace")} 로 {@link LeaveaTrace} 를 주입받는다.
 * 전통적인 eGov 프로젝트는 {@code context-common.xml} 에서 선언하지만,
 * 호환성 가이드 규칙 2의 예외("Xml 외 Java Config, Spring Boot Configuration 방식으로도
 * 적용 가능")에 따라 {@code @Bean} 으로 등록한다.
 *
 * <p>이 빈이 없으면 모든 {@code *ServiceImpl}(규칙 4에 따라 EgovAbstractServiceImpl 을
 * 상속한다) 이 기동 시 의존성 해결에 실패한다.
 *
 * <p>구성:
 * <ul>
 *   <li>{@link DefaultTraceHandler} — 실제 로그 출력 핸들러 (SLF4J 경유 → Logback)</li>
 *   <li>{@link DefaultTraceHandleManager} — 패턴 매칭 + 핸들러 디스패치</li>
 *   <li>{@link LeaveaTrace} — Service 계층의 trace 진입점</li>
 * </ul>
 */
@Configuration
public class EgovCommonConfig {

    @Bean
    public TraceHandler defaultTraceHandler() {
        return new DefaultTraceHandler();
    }

    @Bean
    public TraceHandlerService traceHandlerService(TraceHandler defaultTraceHandler) {
        DefaultTraceHandleManager manager = new DefaultTraceHandleManager();
        AntPathMatcher matcher = new AntPathMatcher();
        // 패키지 구분자 기준 매칭 — 기본값 '/' 로는 클래스 FQN 이 매칭되지 않는다.
        matcher.setPathSeparator(".");
        manager.setReqExpMatcher(matcher);
        manager.setPatterns(new String[] { "*" });
        manager.setHandlers(new TraceHandler[] { defaultTraceHandler });
        return manager;
    }

    /**
     * {@code EgovAbstractServiceImpl} 이 필수 의존하는 빈.
     * <b>빈 이름은 반드시 {@code leaveaTrace}</b> — 실행환경이 이름으로 주입한다.
     */
    @Bean(name = "leaveaTrace")
    public LeaveaTrace leaveaTrace(TraceHandlerService traceHandlerService) {
        LeaveaTrace lt = new LeaveaTrace();
        lt.setTraceHandlerServices(new TraceHandlerService[] { traceHandlerService });
        return lt;
    }
}
