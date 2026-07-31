package com.gonet.config.interceptor;

import com.gonet.common.audit.AuditContext;
import com.gonet.common.base.BaseEntity;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;

/**
 * MyBatis Interceptor — INSERT/UPDATE 시 6종 감사컬럼 자동 주입.
 *
 * <p>대상:
 * <ul>
 *   <li>파라미터가 {@link BaseEntity} 인 경우 → 필드 직접 세팅</li>
 *   <li>파라미터가 {@code Map} 인 경우 → createdBy/createdIp/createdAt/updatedBy/updatedIp/updatedAt 키 주입</li>
 *   <li>Collection 인 경우 각 요소 재귀 적용</li>
 * </ul>
 *
 * <p>매퍼 XML 에서는 감사컬럼에 {@code #{createdBy}} 등을 그대로 사용.
 *
 * <p>덮어쓰기 정책:
 * <ul>
 *   <li>INSERT 시 {@code created_*} 는 값이 없을 때만 세팅(배치/seed 등 명시적 케이스 보호)</li>
 *   <li>INSERT 시 {@code updated_*} 는 {@code created_*} 와 동일 값으로 초기화</li>
 *   <li>UPDATE 시 {@code updated_*} 는 <b>항상</b> 현재 사용자/IP/시각으로 덮어쓴다.
 *       서비스가 {@code findById → modify → update} 패턴으로 엔티티를 재사용할 때
 *       DB 에서 읽어온 stale 값 ("SYSTEM", 과거 시각 등) 을 그대로 다시 INSERT 하지 않기 위함.</li>
 * </ul>
 */
@Component
@Intercepts({
    @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class})
})
public class AuditInterceptor implements Interceptor {

    private static final Logger log = LoggerFactory.getLogger(AuditInterceptor.class);

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        log.info("===AuditInterceptor.intercept executed for method: {}", invocation.getMethod().getName());
        Object[] args = invocation.getArgs();
        MappedStatement ms = (MappedStatement) args[0];
        SqlCommandType type = ms.getSqlCommandType();

        if (type == SqlCommandType.INSERT || type == SqlCommandType.UPDATE) {
            Object param = args[1];
            inject(param, type);
        }
        return invocation.proceed();
    }

    @SuppressWarnings("rawtypes")
    private void inject(Object param, SqlCommandType type) {
        if (param == null) return;

        if (param instanceof Collection<?> col) {
            for (Object o : col) inject(o, type);
            return;
        }

        String userId = AuditContext.userId();
        String ip     = AuditContext.ip();
        LocalDateTime now = LocalDateTime.now();

        if (param instanceof BaseEntity e) {
            if (type == SqlCommandType.INSERT) {
                if (e.getCreatedBy() == null) e.setCreatedBy(userId);
                if (e.getCreatedIp() == null) e.setCreatedIp(ip);
                if (e.getCreatedAt() == null) e.setCreatedAt(now);
                // INSERT 시 updated_* 는 created_* 와 동일값으로 초기화
                if (e.getUpdatedBy() == null) e.setUpdatedBy(e.getCreatedBy());
                if (e.getUpdatedIp() == null) e.setUpdatedIp(e.getCreatedIp());
                if (e.getUpdatedAt() == null) e.setUpdatedAt(e.getCreatedAt());
            } else { // UPDATE — 항상 현재 사용자/IP/시각으로 덮어쓴다
                e.setUpdatedBy(userId);
                e.setUpdatedIp(ip);
                e.setUpdatedAt(now);
            }
            return;
        }

        if (param instanceof Map map) {
            if (type == SqlCommandType.INSERT) {
                map.putIfAbsent("createdBy", userId);
                map.putIfAbsent("createdIp", ip);
                map.putIfAbsent("createdAt", now);
                map.putIfAbsent("updatedBy", map.get("createdBy"));
                map.putIfAbsent("updatedIp", map.get("createdIp"));
                map.putIfAbsent("updatedAt", map.get("createdAt"));
            } else { // UPDATE — 항상 덮어쓴다
                map.put("updatedBy", userId);
                map.put("updatedIp", ip);
                map.put("updatedAt", now);
            }
        }
    }
}
