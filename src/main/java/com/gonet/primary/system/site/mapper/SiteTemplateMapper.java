package com.gonet.primary.system.site.mapper;

import com.gonet.primary.system.site.dto.TemplateInfo;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 사이트 상세·폼 화면의 템플릿 선택 UI 전용 경량 Mapper.
 *
 * <p>2026-04-23c 부터 템플릿은 전역이므로 사이트 필터 없이 전체 활성 템플릿을 조회.
 */
@EgovMapper
public interface SiteTemplateMapper {

    /** 전역 활성 템플릿 목록 (드롭다운/라디오용) */
    List<TemplateInfo> findAllActive();

    /** 템플릿 단건 — 존재 검증용 (template_id 세팅 전 체크) */
    TemplateInfo findById(@Param("templateId") String templateId);
}
