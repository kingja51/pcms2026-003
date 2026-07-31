package com.gonet.primary.system.code.service;

import com.gonet.primary.system.code.dto.Code;
import com.gonet.primary.system.code.dto.CodeOption;
import com.gonet.primary.system.code.dto.CodeSaveForm;
import com.gonet.primary.system.code.dto.CodeSearch;

import java.util.List;

public interface CodeService {

    // CRUD
    List<Code> search(CodeSearch search);
    int        count(CodeSearch search);
    Code       get(String codeId);

    String create(CodeSaveForm form);
    void   update(CodeSaveForm form);
    void   softDelete(String codeId);

    // Thymeleaf 헬퍼 (캐시)
    List<CodeOption> options(String groupCode);
    String           label(String groupCode, String code);
}
