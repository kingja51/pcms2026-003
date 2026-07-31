package com.gonet.config.env;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 필수 환경변수 미주입 시 <b>기동을 즉시 중단</b>한다.
 *
 * <h2>왜 필요한가</h2>
 * yml 에 기본값 없는 {@code ${VAR}} 를 쓰는 것만으로는 fail-fast 가 보장되지 않는다.
 * Spring 은 <b>바인딩되는 프로퍼티만</b> placeholder 를 해석하므로,
 * 아직 {@code @ConfigurationProperties} 빈이 없는 키({@code PCMS_PII_MASTER_KEY} 등)는
 * 미주입 상태로도 앱이 정상 기동한다.
 *
 * <p>바인딩되는 키조차 실패 방식이 나쁘다 — placeholder 문자열이 그대로 흘러가
 * {@code Driver ... claims to not accept jdbcUrl, ${PCMS_DB_PRIMARY_URL}} 같은
 * 원인이 드러나지 않는 오류로 나타난다(2026-07-31 실측).
 *
 * <h2>동작</h2>
 * 애플리케이션 자신의 설정 파일({@code application*.yml})에서 온 프로퍼티만 훑어
 * <b>원본 값</b>에 남아 있는 {@code ${...}} 를 해석해 본다. 해석 실패 = 미주입이다.
 *
 * <p>필수 키 목록을 코드에 두지 않는다 — yml 이 곧 목록이다. 키가 늘거나 줄어도
 * 이 클래스는 손댈 필요가 없다. 기본값이 있는 {@code ${VAR:기본값}} 은 해석되므로 걸리지 않는다.
 *
 * <p>등록: {@code META-INF/spring/org.springframework.boot.env.EnvironmentPostProcessor.imports}.
 * 로깅 시스템 초기화 이전에 실행되므로 로거를 쓰지 않고 예외 메시지로 알린다.
 */
public class RequiredPropertyValidator implements EnvironmentPostProcessor {

    /** {@code ${...}} 안에 중첩 {@code $} 가 없는 단순 placeholder. */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}$]+)}");

    /**
     * 검사 대상 PropertySource 이름 조각.
     * 애플리케이션 yml 만 본다 — 시스템 환경변수·JVM 인자·Boot 내부 소스는 제외한다.
     */
    private static final String CONFIG_SOURCE_MARK = "application";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication application) {
        Set<String> missing = new LinkedHashSet<>();
        List<String> where = new ArrayList<>();

        for (PropertySource<?> source : env.getPropertySources()) {
            if (!(source instanceof EnumerablePropertySource<?> eps)) continue;
            if (!source.getName().contains(CONFIG_SOURCE_MARK)) continue;

            for (String key : eps.getPropertyNames()) {
                Object raw = eps.getProperty(key);
                if (!(raw instanceof String text) || !text.contains("${")) continue;

                Matcher m = PLACEHOLDER.matcher(text);
                while (m.find()) {
                    String expr = m.group(1);
                    // 기본값이 있으면(:) 해석에 실패하지 않으므로 검사할 필요가 없다.
                    if (expr.indexOf(':') >= 0) continue;
                    try {
                        env.resolveRequiredPlaceholders("${" + expr + "}");
                    // Spring 6.2 의 PlaceholderResolutionException 은 IllegalArgumentException 하위다.
                    } catch (IllegalArgumentException e) {
                        if (missing.add(expr)) {
                            where.add("  · " + expr + "   ← " + key);
                        }
                    }
                }
            }
        }

        if (!missing.isEmpty()) {
            throw new IllegalStateException(buildMessage(where));
        }
    }

    private String buildMessage(List<String> where) {
        StringBuilder sb = new StringBuilder();
        sb.append(System.lineSeparator())
          .append("============================================================").append(System.lineSeparator())
          .append(" 필수 환경변수가 주입되지 않아 기동을 중단합니다 (의도된 fail-fast)").append(System.lineSeparator())
          .append("============================================================").append(System.lineSeparator());
        where.forEach(line -> sb.append(line).append(System.lineSeparator()));
        sb.append(System.lineSeparator())
          .append(" 조치: .env.example 를 .env 로 복사한 뒤 __CHANGE_ME__ 를 채우고,").append(System.lineSeparator())
          .append("       실행 환경에 환경변수로 주입하세요.").append(System.lineSeparator())
          .append("       키별 용도·발급처는 .env.key.example 을 참고하세요.").append(System.lineSeparator())
          .append("       (운영은 deploy/tomcat 의 setenv 스크립트로 주입)").append(System.lineSeparator())
          .append("============================================================");
        return sb.toString();
    }
}
