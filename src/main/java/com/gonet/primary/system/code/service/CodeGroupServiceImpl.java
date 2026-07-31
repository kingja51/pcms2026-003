package com.gonet.primary.system.code.service;

import com.gonet.common.util.UuidV7Generator;
import com.gonet.config.cache.CacheConfig;
import com.gonet.primary.system.code.dto.CodeGroup;
import com.gonet.primary.system.code.dto.CodeGroupSaveForm;
import com.gonet.primary.system.code.dto.CodeGroupSearch;
import com.gonet.primary.system.code.mapper.CodeGroupMapper;
import org.egovframe.rte.fdl.cmmn.EgovAbstractServiceImpl;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * 공통코드 그룹 CRUD 서비스.
 *
 * <p>그룹 변경 시 {@link CacheConfig#CODE_GROUP} 캐시 전역 무효화 —
 * group_code ↔ code_group_id 재해석이 필요하므로 세밀 무효화보다 전체 flush 가 단순·안전.
 */
@Service
@Transactional(readOnly = true, transactionManager =
    com.gonet.config.datasource.PrimaryDataSourceConfig.TRANSACTION_MGR)
public class CodeGroupServiceImpl extends EgovAbstractServiceImpl implements CodeGroupService {

    private final CodeGroupMapper mapper;

    public CodeGroupServiceImpl(CodeGroupMapper mapper) {
        this.mapper = mapper;
    }

    @Override public List<CodeGroup> search(CodeGroupSearch s) { return mapper.findList(normalize(s)); }
    @Override public int             count(CodeGroupSearch s) { return mapper.countList(normalize(s)); }
    @Override public CodeGroup       get(String id) {
        if (id == null || id.isBlank()) return null;
        return mapper.findById(id);
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = com.gonet.config.datasource.PrimaryDataSourceConfig.TRANSACTION_MGR)
    @CacheEvict(cacheNames = CacheConfig.CODE_GROUP, allEntries = true)
    public String create(CodeGroupSaveForm form) {
        Objects.requireNonNull(form, "form");
        String code = form.getGroupCode().trim().toUpperCase();
        if (mapper.existsByCode(code, null) > 0) {
            throw new IllegalArgumentException("이미 사용 중인 그룹 코드입니다: " + code);
        }
        CodeGroup g = new CodeGroup();
        g.setCodeGroupId(UuidV7Generator.generate("CG0"));
        g.setGroupCode(code);
        g.setGroupName(form.getGroupName().trim());
        g.setDescription(blankToNull(form.getDescription()));
        g.setUseYn(form.getUseYn() == null ? "Y" : form.getUseYn());
        try {
            mapper.insert(g);
        } catch (DuplicateKeyException dup) {
            throw new IllegalArgumentException("이미 존재하는 그룹 코드입니다.", dup);
        }
        return g.getCodeGroupId();
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = com.gonet.config.datasource.PrimaryDataSourceConfig.TRANSACTION_MGR)
    @CacheEvict(cacheNames = CacheConfig.CODE_GROUP, allEntries = true)
    public void update(CodeGroupSaveForm form) {
        Objects.requireNonNull(form, "form");
        if (form.getCodeGroupId() == null || form.getCodeGroupId().isBlank()) {
            throw new IllegalArgumentException("codeGroupId required");
        }
        CodeGroup existing = mapper.findById(form.getCodeGroupId());
        if (existing == null) throw new IllegalArgumentException("그룹을 찾을 수 없습니다.");

        String code = form.getGroupCode().trim().toUpperCase();
        if (mapper.existsByCode(code, form.getCodeGroupId()) > 0) {
            throw new IllegalArgumentException("이미 사용 중인 그룹 코드입니다: " + code);
        }
        existing.setGroupCode(code);
        existing.setGroupName(form.getGroupName().trim());
        existing.setDescription(blankToNull(form.getDescription()));
        existing.setUseYn(form.getUseYn());
        mapper.update(existing);
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = com.gonet.config.datasource.PrimaryDataSourceConfig.TRANSACTION_MGR)
    @CacheEvict(cacheNames = CacheConfig.CODE_GROUP, allEntries = true)
    public void softDelete(String codeGroupId) {
        if (codeGroupId == null || codeGroupId.isBlank()) {
            throw new IllegalArgumentException("codeGroupId required");
        }
        int items = mapper.countActiveCodes(codeGroupId);
        if (items > 0) {
            throw new IllegalArgumentException(
                "이 그룹에 " + items + "개의 코드가 있어 삭제할 수 없습니다. 먼저 하위 코드를 정리하세요.");
        }
        int n = mapper.softDelete(codeGroupId);
        if (n == 0) throw new IllegalArgumentException("이미 삭제되었거나 존재하지 않는 그룹입니다.");
    }

    private static CodeGroupSearch normalize(CodeGroupSearch s) { return s == null ? new CodeGroupSearch() : s; }
    private static String blankToNull(String s) { return (s == null || s.isBlank()) ? null : s.trim(); }
}
