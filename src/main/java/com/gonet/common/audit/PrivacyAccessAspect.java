package com.gonet.common.audit;

import com.gonet.logging.privacy.dto.PrivacyAccessEvent;
import com.gonet.logging.privacy.dto.PrivacyAccessLog;
import com.gonet.logging.privacy.service.PrivacyAccessLogger;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * {@link PrivacyAccess} 어노테이션 메서드 진입/종료 감싸 {@link PrivacyAccessLogger} 발행.
 *
 * <p>실행 흐름:
 * <ol>
 *   <li>메서드 정상 종료 → {@code result = SUCCESS}</li>
 *   <li>{@link AccessDeniedException} → {@code result = DENIED} (PIPA — 권한거부 접근시도도 적재)</li>
 *   <li>그 외 예외 → {@code result = ERROR}</li>
 * </ol>
 *
 * <p>예외는 항상 재던진다 — 적재만 하고 흐름은 그대로.
 *
 * <p>Order: {@link Ordered#LOWEST_PRECEDENCE} - 1 — 트랜잭션 advisor(LOWEST_PRECEDENCE) 보다 안쪽,
 * 즉 트랜잭션 내부에서 호출되도록 한다. 단, 적재는 비동기로 돌므로 외부 TX 와 무관.
 */
@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 1)
public class PrivacyAccessAspect {

    private static final Logger log = LoggerFactory.getLogger(PrivacyAccessAspect.class);

    /**
     * 적재 enqueue 가 실패한 경우 마지막 보루로 사용하는 별도 SLF4J 로거.
     * Logback 설정에서 {@code com.gonet.privacy.fallback} 로 별도 RollingFileAppender 를
     * 두면 SIEM/감사 도구가 파일을 직접 수집해 PIPA 누락을 탐지할 수 있다.
     * 메인 logger 와 분리하는 이유: 운영 모니터링/알림이 fallback 발생 자체를 시그널로 인식.
     */
    private static final Logger fallback = LoggerFactory.getLogger("com.gonet.privacy.fallback");

    private final PrivacyAccessLogger logger;

    public PrivacyAccessAspect(PrivacyAccessLogger logger) {
        this.logger = logger;
    }

    @Around("@annotation(com.gonet.common.audit.PrivacyAccess)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        Method method = ((MethodSignature) pjp.getSignature()).getMethod();
        PrivacyAccess ann = method.getAnnotation(PrivacyAccess.class);
        if (ann == null) {
            return pjp.proceed();
        }
        Object retval;
        String result = PrivacyAccessLog.RESULT_SUCCESS;
        String failReason = null;
        try {
            retval = pjp.proceed();
            return retval;
        } catch (AccessDeniedException ex) {
            result = PrivacyAccessLog.RESULT_DENIED;
            failReason = ex.getMessage();
            throw ex;
        } catch (RuntimeException ex) {
            result = PrivacyAccessLog.RESULT_ERROR;
            failReason = ex.getMessage();
            throw ex;
        } finally {
            // PIPA 준수 관점에서 적재 누락은 비즈니스 차단보다 손실이 크다.
            // enqueue 자체가 실패하면 별도 SLF4J 로거(file appender) 로 fallback 라인을 기록 —
            // 운영 측 SIEM 이 그 라인을 수집해 누락 이벤트로 alert 할 수 있다.
            String action = ann.action();
            String entity = ann.entity();
            try {
                PrivacyAccessEvent event = PrivacyAccessEvent.of(action, entity)
                    .withResult(result);
                if (!ann.fields().isBlank())          event.setPiiFields(ann.fields());
                if (!ann.targetUserType().isBlank())  event.setTargetUserType(ann.targetUserType());
                if (failReason != null)               event.withFailReason(failReason);
                logger.write(event);
            } catch (Exception ex) {
                log.warn("PRIVACY_ACCESS_ASPECT_FAIL method={} reason={}",
                    method.getName(), ex.getMessage());
                // file fallback — 적재 큐 자체가 깨졌을 때 마지막 보루
                fallback.error(
                    "PRIVACY_ACCESS_FALLBACK action={} entity={} result={} method={} reason={}",
                    action, entity, result, method.getName(), ex.getMessage());
            }
        }
    }
}
