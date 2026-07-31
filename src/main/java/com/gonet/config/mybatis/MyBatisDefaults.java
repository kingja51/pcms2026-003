package com.gonet.config.mybatis;

import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;

/**
 * 3-DB 공통 MyBatis Configuration 생성기.
 *
 * <p>{@code SqlSessionFactoryBean.setConfiguration(Configuration)} 에 전달해야 실제 반영된다.
 * {@code setConfigurationProperties(Properties)} 는 XML 내 {@code ${var}} 치환용이라
 * {@code mapUnderscoreToCamelCase} 같은 Configuration 설정은 적용되지 않는다.
 */
final class MyBatisDefaults {

    private MyBatisDefaults() {}

    static Configuration newConfiguration() {
        Configuration c = new Configuration();
        c.setMapUnderscoreToCamelCase(true);
        c.setCacheEnabled(false);
        c.setDefaultFetchSize(100);
        c.setDefaultStatementTimeout(30);
        c.setJdbcTypeForNull(JdbcType.NULL);
        // 모든 매퍼 SQL 로거 이름에 "gopcms.sql." 접두어를 붙인다.
        // → logback 에서 logger name="gopcms.sql" 하나로 전체 SQL 을 전용 appender 로 분리한다.
        c.setLogPrefix("gopcms.sql.");
        return c;
    }
}
