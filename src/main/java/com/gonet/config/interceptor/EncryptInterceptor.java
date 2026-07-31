package com.gonet.config.interceptor;

import com.gonet.common.crypto.AesGcmCipher;
import com.gonet.common.crypto.Encrypt;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.executor.resultset.ResultSetHandler;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * MyBatis Interceptor — {@link Encrypt} 어노테이션 필드 자동 AES-GCM 암복호화.
 *
 * <p>두 지점을 가로챈다:
 * <ul>
 *   <li>{@link ParameterHandler#setParameters(PreparedStatement)} — INSERT/UPDATE 파라미터 암호화 (before)</li>
 *   <li>{@link ResultSetHandler#handleResultSets(Statement)} — SELECT 결과 복호화 (after)</li>
 * </ul>
 *
 * <p>저장 전: 평문 → {AG}base64(iv||ct||tag)
 * <p>조회 후: {AG} 프리픽스면 복호화, 없으면 legacy plaintext 그대로 (이전 데이터 호환)
 *
 * <p>Collection 및 중첩 객체도 재귀 처리. private final 및 Static 필드는 스킵.
 */
@Component
@Intercepts({
    @Signature(type = ParameterHandler.class, method = "setParameters", args = PreparedStatement.class),
    @Signature(type = ResultSetHandler.class, method = "handleResultSets", args = Statement.class)
})
public class EncryptInterceptor implements Interceptor {

    private static final Logger log = LoggerFactory.getLogger(EncryptInterceptor.class);

    private final AesGcmCipher cipher;

    /**
     * 클래스별 {@code @Encrypt} 필드 메타데이터 캐시.
     * 매 호출마다 {@code getDeclaredFields()} + 슈퍼클래스 walk + 어노테이션 검사를 반복하면
     * 처리량 큰 도메인에서 reflection 비용이 누적된다. 첫 walk 결과를 캐시해 50%+ 절감.
     *
     * <p>"@Encrypt 필드가 없는 클래스" 도 빈 리스트로 캐시 — 두 번째 호출부터 walk 자체 skip.
     * setAccessible(true) 도 캐시 시점에 한 번만 호출.
     */
    private static final ConcurrentMap<Class<?>, List<Field>> ENCRYPT_FIELDS = new ConcurrentHashMap<>();

    public EncryptInterceptor(AesGcmCipher cipher) {
        this.cipher = cipher;
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Object target = invocation.getTarget();

        if (target instanceof ParameterHandler ph) {
            // 저장 직전 파라미터 DTO 의 @Encrypt 필드를 암호문으로 치환하되,
            // PreparedStatement 바인딩(proceed) 이 끝나면 원본 평문으로 되돌린다.
            // 이렇게 하지 않으면 MyBatis 가 넘겨준 호출자 DTO 가 암호문으로 오염되어
            // insert 이후 그 필드를 재사용하는 코드(예: 가입 직후 환영메일 수신자=email)가
            // 암호문을 그대로 사용하게 된다.
            List<FieldRestore> restores = new ArrayList<>();
            walkEncrypt(ph.getParameterObject(), restores);
            try {
                return invocation.proceed();
            } finally {
                for (int i = restores.size() - 1; i >= 0; i--) {
                    restores.get(i).restore();
                }
            }
        }

        // ResultSetHandler
        Object result = invocation.proceed();
        walkDecrypt(result);
        return result;
    }

    /** 저장 파라미터 암호화 — 치환한 필드는 {@code restores} 에 원본 평문과 함께 기록. */
    private void walkEncrypt(Object obj, List<FieldRestore> restores) {
        if (obj == null) return;

        if (obj instanceof Collection<?> col) {
            for (Object o : col) walkEncrypt(o, restores);
            return;
        }

        Class<?> type = obj.getClass();
        if (isPrimitiveLike(type)) return;

        for (Field f : encryptFieldsOf(type)) {
            try {
                Object v = f.get(obj);
                if (v instanceof String s && !cipher.isEncrypted(s)) {
                    f.set(obj, cipher.encrypt(s));
                    restores.add(new FieldRestore(f, obj, s));
                }
            } catch (IllegalAccessException ignore) {
                // skip — 접근 불가 필드는 무시
            }
        }
    }

    /** 조회 결과 복호화. */
    private void walkDecrypt(Object obj) {
        if (obj == null) return;

        if (obj instanceof Collection<?> col) {
            for (Object o : col) walkDecrypt(o);
            return;
        }

        Class<?> type = obj.getClass();
        if (isPrimitiveLike(type)) return;

        for (Field f : encryptFieldsOf(type)) {
            try {
                Object v = f.get(obj);
                if (v instanceof String s && cipher.isEncrypted(s)) {
                    f.set(obj, cipher.decryptIfEncrypted(s));
                }
            } catch (IllegalAccessException ignore) {
                // skip — 접근 불가 필드는 무시
            } catch (RuntimeException decryptEx) {
                // {AG} 프리픽스만 가진 위조·손상 값이 섞이면 복호화가 예외를 던진다.
                // 여기서 격리하지 않으면 그 행을 포함한 목록/상세 쿼리 전체가 실패(행 1건으로 화면 DoS).
                // 필드 단위로 삼키고 원본값을 그대로 둔다(로그로 추적).
                log.warn("PII decrypt skipped — field={} reason={}", f.getName(), decryptEx.getMessage());
            }
        }
    }

    /** proceed 이후 호출자 DTO 필드를 원본 평문으로 복원하기 위한 스냅샷. */
    private record FieldRestore(Field field, Object target, String original) {
        void restore() {
            try {
                field.set(target, original);
            } catch (IllegalAccessException ignore) {
                // skip
            }
        }
    }

    /**
     * {@code type} 의 {@code @Encrypt} String 필드 목록을 캐시해 반환.
     * 슈퍼클래스 chain 까지 walk. {@code setAccessible(true)} 도 캐시 시점에 한 번만 호출.
     */
    private static List<Field> encryptFieldsOf(Class<?> type) {
        List<Field> cached = ENCRYPT_FIELDS.get(type);
        if (cached != null) return cached;
        List<Field> found = new ArrayList<>();
        Class<?> cur = type;
        while (cur != null && cur != Object.class) {
            for (Field f : cur.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                if (f.isAnnotationPresent(Encrypt.class) && f.getType() == String.class) {
                    f.setAccessible(true);
                    found.add(f);
                }
            }
            cur = cur.getSuperclass();
        }
        // 빈 리스트도 캐시 — "이 클래스에 @Encrypt 없음" 결정 자체가 재사용 가치 있음
        ENCRYPT_FIELDS.put(type, found);
        return found;
    }

    private boolean isPrimitiveLike(Class<?> t) {
        return t.isPrimitive()
            || t == String.class
            || Number.class.isAssignableFrom(t)
            || t == Boolean.class
            || t == Character.class
            || t.isEnum()
            || t.getName().startsWith("java.time.")
            || t.getName().startsWith("java.util.Date")
            || t.getName().startsWith("java.sql.");
    }
}
