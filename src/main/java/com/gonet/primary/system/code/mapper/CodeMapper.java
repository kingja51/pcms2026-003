package com.gonet.primary.system.code.mapper;

import com.gonet.primary.system.code.dto.Code;
import com.gonet.primary.system.code.dto.CodeOption;
import com.gonet.primary.system.code.dto.CodeSearch;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * tb_code CRUD + Thymeleaf 헬퍼용 경량 조회.
 */
@EgovMapper
public interface CodeMapper {

    List<Code> findList(@Param("search") CodeSearch search);
    int        countList(@Param("search") CodeSearch search);
    Code       findById(@Param("codeId") String codeId);

    int existsByCode(@Param("codeGroupId") String codeGroupId,
                      @Param("code") String code,
                      @Param("excludeCodeId") String excludeCodeId);

    /** Thymeleaf 드롭다운용 — group_code 기준 활성 코드 목록. */
    List<CodeOption> findOptionsByGroupCode(@Param("groupCode") String groupCode);

    /** Thymeleaf 레이블용 — 단건 조회. 캐시 키: groupCode + ':' + code. */
    String findCodeNameByGroupAndCode(@Param("groupCode") String groupCode,
                                       @Param("code") String code);

    void insert(Code code);
    void update(Code code);
    int  softDelete(@Param("codeId") String codeId);
}
