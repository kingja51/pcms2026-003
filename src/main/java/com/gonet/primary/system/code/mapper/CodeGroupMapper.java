package com.gonet.primary.system.code.mapper;

import com.gonet.primary.system.code.dto.CodeGroup;
import com.gonet.primary.system.code.dto.CodeGroupSearch;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * tb_code_group CRUD.
 */
@EgovMapper
public interface CodeGroupMapper {

    List<CodeGroup> findList(@Param("search") CodeGroupSearch search);
    int             countList(@Param("search") CodeGroupSearch search);
    CodeGroup       findById(@Param("codeGroupId") String codeGroupId);
    CodeGroup       findByGroupCode(@Param("groupCode") String groupCode);

    int existsByCode(@Param("groupCode") String groupCode,
                      @Param("excludeCodeGroupId") String excludeCodeGroupId);

    int countActiveCodes(@Param("codeGroupId") String codeGroupId);

    void insert(CodeGroup group);
    void update(CodeGroup group);
    int  softDelete(@Param("codeGroupId") String codeGroupId);
}
