package com.gonet.config.mybatis;

import com.gonet.config.datasource.SecondaryDataSourceConfig;
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
 * Secondary MyBatis 설정 — 개별 프로그램·외부 API 영역.
 *
 * <p>스캔: {@code com.gonet.secondary} 하위 {@link EgovMapper} 인터페이스 /
 * XML: {@code classpath*:mapper/secondary/**}{@code /*_maria.xml}.
 * 상세 규약은 {@link PrimaryMyBatisConfig} 주석 참조(호환성 규칙 5).
 */
@Configuration
public class SecondaryMyBatisConfig {

    public static final String SQL_SESSION_FACTORY  = "secondarySqlSessionFactory";
    public static final String SQL_SESSION_TEMPLATE = "secondarySqlSessionTemplate";

    @Bean
    public static MapperConfigurer secondaryMapperConfigurer() {
        MapperConfigurer configurer = new MapperConfigurer();
        configurer.setBasePackage("com.gonet.secondary");
        configurer.setAnnotationClass(EgovMapper.class);
        configurer.setSqlSessionFactoryBeanName(SQL_SESSION_FACTORY);
        return configurer;
    }

    @Bean(name = SQL_SESSION_FACTORY)
    public SqlSessionFactory secondarySqlSessionFactory(
            @Qualifier(SecondaryDataSourceConfig.DATA_SOURCE) DataSource dataSource) throws Exception {

        SqlSessionFactoryBean bean = new SqlSessionFactoryBean();
        bean.setDataSource(dataSource);
        bean.setMapperLocations(
            new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mapper/secondary/**/*_maria.xml")
        );
        bean.setTypeAliasesPackage("com.gonet.secondary");
        bean.setConfiguration(MyBatisDefaults.newConfiguration());
        return bean.getObject();
    }

    @Bean(name = SQL_SESSION_TEMPLATE)
    public SqlSessionTemplate secondarySqlSessionTemplate(
            @Qualifier(SQL_SESSION_FACTORY) SqlSessionFactory factory) {
        return new SqlSessionTemplate(factory);
    }
}
