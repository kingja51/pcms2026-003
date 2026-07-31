package com.gonet.config.mybatis;

import com.gonet.config.datasource.PrimaryDataSourceConfig;
import org.apache.ibatis.session.SqlSessionFactory;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;
import org.egovframe.rte.psl.dataaccess.mapper.MapperConfigurer;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import javax.sql.DataSource;

/**
 * Primary MyBatis 설정.
 *
 * <ul>
 *   <li>매퍼 스캔 : {@code com.gonet.primary} 하위의 {@link EgovMapper} 인터페이스</li>
 *   <li>매퍼 XML  : {@code classpath*:mapper/primary/**}{@code /*_maria.xml} (MariaDB 단일)</li>
 *   <li>팩토리    : {@code primarySqlSessionFactory}</li>
 * </ul>
 *
 * <p><b>호환성 규칙 5</b> — MyBatis 의 {@code @MapperScan}/{@code @Mapper} 를 쓰지 않는다.
 * 표준프레임워크가 제공하는 {@link MapperConfigurer}({@code MapperScannerConfigurer} 상속) 로
 * 스캔하고, 매퍼 인터페이스에는 {@link EgovMapper} 를 붙인다.
 * ({@code @Mapper} 는 실행환경 v4.3 이하 표기라 5.0 기준 위반이다. 001 은 이 방식이었다)
 *
 * <p>{@code annotationClass = EgovMapper.class} 필터 덕분에 basePackage 를 도메인별로 열거하지 않고
 * {@code com.gonet.primary} 광역으로 둘 수 있다. Service/DTO 인터페이스는 애노테이션이 없어
 * 스캔 대상에서 자동 제외된다.
 *
 * <p>{@code MapperConfigurer} 는 {@code BeanDefinitionRegistryPostProcessor} 다 —
 * {@code static @Bean} 으로 선언해야 컨테이너가 일반 빈을 조기 초기화하지 않는다.
 * 같은 이유로 팩토리는 인스턴스가 아니라 <b>빈 이름</b>으로 연결한다.
 */
@Configuration
public class PrimaryMyBatisConfig {

    public static final String SQL_SESSION_FACTORY  = "primarySqlSessionFactory";
    public static final String SQL_SESSION_TEMPLATE = "primarySqlSessionTemplate";

    @Bean
    public static MapperConfigurer primaryMapperConfigurer() {
        MapperConfigurer configurer = new MapperConfigurer();
        configurer.setBasePackage("com.gonet.primary");
        configurer.setAnnotationClass(EgovMapper.class);
        configurer.setSqlSessionFactoryBeanName(SQL_SESSION_FACTORY);
        return configurer;
    }

    @Primary
    @Bean(name = SQL_SESSION_FACTORY)
    public SqlSessionFactory primarySqlSessionFactory(
            @Qualifier(PrimaryDataSourceConfig.DATA_SOURCE) DataSource dataSource) throws Exception {

        SqlSessionFactoryBean bean = new SqlSessionFactoryBean();
        bean.setDataSource(dataSource);
        bean.setMapperLocations(
            new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mapper/primary/**/*_maria.xml")
        );
        bean.setTypeAliasesPackage("com.gonet.primary");
        bean.setConfiguration(MyBatisDefaults.newConfiguration());
        // 인터셉터(감사컬럼 주입 AuditInterceptor / PII 투명 암복호 EncryptInterceptor)는
        // P1 공통 기반 계층에서 도입하며, 그때 setPlugins(...) 로 연결한다.
        return bean.getObject();
    }

    @Primary
    @Bean(name = SQL_SESSION_TEMPLATE)
    public SqlSessionTemplate primarySqlSessionTemplate(
            @Qualifier(SQL_SESSION_FACTORY) SqlSessionFactory factory) {
        return new SqlSessionTemplate(factory);
    }
}
