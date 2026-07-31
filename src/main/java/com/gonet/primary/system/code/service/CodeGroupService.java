package com.gonet.primary.system.code.service;

import com.gonet.primary.system.code.dto.CodeGroup;
import com.gonet.primary.system.code.dto.CodeGroupSaveForm;
import com.gonet.primary.system.code.dto.CodeGroupSearch;

import java.util.List;

public interface CodeGroupService {

    List<CodeGroup> search(CodeGroupSearch search);
    int             count(CodeGroupSearch search);
    CodeGroup       get(String codeGroupId);

    String create(CodeGroupSaveForm form);
    void   update(CodeGroupSaveForm form);
    void   softDelete(String codeGroupId);
}
