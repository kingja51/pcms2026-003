package com.gonet.primary.system.site.mapper;

import com.gonet.primary.system.site.dto.Site;
import com.gonet.primary.system.site.dto.SiteSearch;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * tb_site CRUD Mapper.
 *
 * <p>eGov 호환성: {@code @Mapper} 어노테이션 필수 (ArchUnit R2).
 */
@EgovMapper
public interface SiteMapper {

    List<Site> findList(@Param("search") SiteSearch search);

    int countList(@Param("search") SiteSearch search);

    Site findById(@Param("siteId") String siteId);

    /** site_code 로 단건 조회 (대소문자 민감). 활성·비삭제만. */
    Site findByCode(@Param("siteCode") String siteCode);

    int existsByCode(@Param("siteCode") String siteCode,
                      @Param("excludeSiteId") String excludeSiteId);

    int existsByDomain(@Param("domain") String domain,
                        @Param("excludeSiteId") String excludeSiteId);

    int insert(Site site);

    int update(Site site);

    /** 기본 템플릿 1건 변경 — template_id */
    int updateDefaultTemplate(@Param("siteId") String siteId,
                               @Param("templateId") String templateId);

    /** 소프트 삭제 — delete_yn='Y' */
    int softDelete(@Param("siteId") String siteId);
}
