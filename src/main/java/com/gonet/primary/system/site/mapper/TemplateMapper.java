package com.gonet.primary.system.site.mapper;

import com.gonet.primary.system.site.dto.Template;
import com.gonet.primary.system.site.dto.TemplateSearch;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * tb_template CRUD Mapper (관리자용).
 *
 * <p>선택 UI 전용 조회는 {@link SiteTemplateMapper} 분리 유지.
 */
@EgovMapper
public interface TemplateMapper {

    List<Template> findList(@Param("search") TemplateSearch search);

    int countList(@Param("search") TemplateSearch search);

    Template findById(@Param("templateId") String templateId);

    /** 전역 코드 중복 체크 (수정 시 자기 자신 제외) */
    int existsByCode(@Param("templateCode") String templateCode,
                      @Param("excludeTemplateId") String excludeTemplateId);

    int insert(Template template);

    int update(Template template);

    int softDelete(@Param("templateId") String templateId);

    /** 해당 템플릿을 default_template_id 로 참조하는 사이트 수 (삭제 전 경고/정리용) */
    int countReferencingSites(@Param("templateId") String templateId);
}
