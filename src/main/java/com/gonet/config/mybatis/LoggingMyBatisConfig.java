package com.gonet.config.mybatis;

import com.gonet.config.datasource.LoggingDataSourceConfig;
import org.apache.ibatis.session.SqlSessionFactory;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;
import org.egovframe.rte.psl.dataaccess.mapper.MapperConfigurer;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import javax.sql.DataSource;

/**
 * Logging MyBatis 설정 — {@code log_*} · {@code stat_*} 적재.
 *
 * <p>스캔: {@code com.gonet.logging} 하위 {@link EgovMapper} 인터페이스 /
 * XML: {@code classpath*:mapper/logging/**}{@code /*_maria.xml}.
 * 상세 규약은 {@link PrimaryMyBatisConfig} 주석 참조(호환성 규칙 5).
 */
@Configuration
public class LoggingMyBatisConfig {

    public static final String SQL_SESSION_FACTORY  = "loggingSqlSessionFactory";
    public static final String SQL_SESSION_TEMPLATE = "loggingSqlSessionTemplate";

    @Bean
    public static MapperConfigurer loggingMapperConfigurer() {
        MapperConfigurer configurer = new MapperConfigurer();
        configurer.setBasePackage("com.gonet.logging");
        configurer.setAnnotationClass(EgovMapper.class);
        configurer.setSqlSessionFactoryBeanName(SQL_SESSION_FACTORY);
        return configurer;
    }

    @Bean(name = SQL_SESSION_FACTORY)
    public SqlSessionFactory loggingSqlSessionFactory(
            @Qualifier(LoggingDataSourceConfig.DATA_SOURCE) DataSource dataSource) throws Exception {

        SqlSessionFactoryBean bean = new SqlSessionFactoryBean();
        bean.setDataSource(dataSource);
        bean.setMapperLocations(
            new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mapper/logging/**/*_maria.xml")
        );
        bean.setTypeAliasesPackage("com.gonet.logging");
        bean.setConfiguration(MyBatisDefaults.newConfiguration());
        return bean.getObject();
    }

    @Bean(name = SQL_SESSION_TEMPLATE)
    public SqlSessionTemplate loggingSqlSessionTemplate(
            @Qualifier(SQL_SESSION_FACTORY) SqlSessionFactory factory) {
        return new SqlSessionTemplate(factory);
    }
}
