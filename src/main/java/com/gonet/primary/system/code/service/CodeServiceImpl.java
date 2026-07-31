package com.gonet.primary.system.code.service;

import com.gonet.common.util.UuidV7Generator;
import com.gonet.config.cache.CacheConfig;
import com.gonet.primary.system.code.dto.Code;
import com.gonet.primary.system.code.dto.CodeOption;
import com.gonet.primary.system.code.dto.CodeSaveForm;
import com.gonet.primary.system.code.dto.CodeSearch;
import com.gonet.primary.system.code.mapper.CodeGroupMapper;
import com.gonet.primary.system.code.mapper.CodeMapper;
import com.gonet.primary.system.code.dto.CodeGroup;
import org.egovframe.rte.fdl.cmmn.EgovAbstractServiceImpl;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * 공통코드 CRUD + Thymeleaf 헬퍼 캐시.
 *
 * <p>캐시 전략:
 * <ul>
 *   <li>{@link #options(String)} — group_code 키, TTL 1h (CacheConfig.CODE_GROUP)</li>
 *   <li>{@link #label(String, String)} — groupCode+":"+code 키, 동일 캐시</li>
 * </ul>
 * CUD 시 전체 flush — 그룹 이름 변경이 옵션 라벨에 전파되어야 하므로 단순·안전.
 */
@Service
@Transactional(readOnly = true, transactionManager =
    com.gonet.config.datasource.PrimaryDataSourceConfig.TRANSACTION_MGR)
public class CodeServiceImpl extends EgovAbstractServiceImpl implements CodeService {

    private final CodeMapper      mapper;
    private final CodeGroupMapper groupMapper;

    public CodeServiceImpl(CodeMapper mapper, CodeGroupMapper groupMapper) {
        this.mapper = mapper;
        this.groupMapper = groupMapper;
    }

    // ------------------------------------------------------------------
    // 조회
    // ------------------------------------------------------------------

    @Override public List<Code> search(CodeSearch s) { return mapper.findList(normalize(s)); }
    @Override public int        count(CodeSearch s) { return mapper.countList(normalize(s)); }

    @Override
    public Code get(String codeId) {
        if (codeId == null || codeId.isBlank()) return null;
        return mapper.findById(codeId);
    }

    // ------------------------------------------------------------------
    // Thymeleaf 헬퍼 (캐시)
    // ------------------------------------------------------------------

    /**
     * <b>M-5 캐시 키 정책</b> — 공통코드 ({@code tb_code_group}) 는 site_id 컬럼이 없는 전역
     * 마스터다. 모든 사이트가 동일한 공통코드를 공유하므로 siteId 를 키에 포함하지 <i>않는다</i>.
     * 멀티사이트 혼용 우려는 데이터 모델 단계에서 차단된 상태.
     *
     * <p>향후 사이트별 코드 도입 시 (a) {@code tb_code_group.site_id} 컬럼 추가 +
     * (b) 본 메서드 시그니처에 {@code siteId} 파라미터 추가 + (c) 캐시 키도
     * {@code "options:" + siteId + ":" + groupCode} 로 확장이 필요.
     */
    @Override
    @Cacheable(cacheNames = CacheConfig.CODE_GROUP, key = "'options:' + #groupCode")
    public List<CodeOption> options(String groupCode) {
        if (groupCode == null || groupCode.isBlank()) return List.of();
        return mapper.findOptionsByGroupCode(groupCode);
    }

    @Override
    @Cacheable(cacheNames = CacheConfig.CODE_GROUP,
               key = "'label:' + #groupCode + ':' + #code",
               unless = "#result == null")
    public String label(String groupCode, String code) {
        if (groupCode == null || groupCode.isBlank() || code == null || code.isBlank()) return null;
        return mapper.findCodeNameByGroupAndCode(groupCode, code);
    }

    // ------------------------------------------------------------------
    // CUD
    // ------------------------------------------------------------------

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = com.gonet.config.datasource.PrimaryDataSourceConfig.TRANSACTION_MGR)
    @CacheEvict(cacheNames = CacheConfig.CODE_GROUP, allEntries = true)
    public String create(CodeSaveForm form) {
        Objects.requireNonNull(form, "form");
        requireGroup(form.getCodeGroupId());
        String code = form.getCode().trim().toUpperCase();

        if (mapper.existsByCode(form.getCodeGroupId(), code, null) > 0) {
            throw new IllegalArgumentException("이미 사용 중인 코드입니다: " + code);
        }

        Code c = new Code();
        c.setCodeId(UuidV7Generator.generate("CD0"));
        c.setCodeGroupId(form.getCodeGroupId());
        c.setCode(code);
        c.setCodeName(form.getCodeName().trim());
        c.setCodeValue(blankToNull(form.getCodeValue()));
        c.setSortOrder(form.getSortOrder() == null ? 0 : form.getSortOrder());
        c.setUseYn(form.getUseYn() == null ? "Y" : form.getUseYn());
        try {
            mapper.insert(c);
        } catch (DuplicateKeyException dup) {
            throw new IllegalArgumentException("이미 존재하는 코드입니다.", dup);
        }
        return c.getCodeId();
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = com.gonet.config.datasource.PrimaryDataSourceConfig.TRANSACTION_MGR)
    @CacheEvict(cacheNames = CacheConfig.CODE_GROUP, allEntries = true)
    public void update(CodeSaveForm form) {
        Objects.requireNonNull(form, "form");
        if (form.getCodeId() == null || form.getCodeId().isBlank()) {
            throw new IllegalArgumentException("codeId required");
        }
        Code existing = mapper.findById(form.getCodeId());
        if (existing == null) throw new IllegalArgumentException("코드를 찾을 수 없습니다.");

        requireGroup(form.getCodeGroupId());
        String code = form.getCode().trim().toUpperCase();
        if (mapper.existsByCode(form.getCodeGroupId(), code, form.getCodeId()) > 0) {
            throw new IllegalArgumentException("이미 사용 중인 코드입니다: " + code);
        }

        existing.setCodeGroupId(form.getCodeGroupId());
        existing.setCode(code);
        existing.setCodeName(form.getCodeName().trim());
        existing.setCodeValue(blankToNull(form.getCodeValue()));
        existing.setSortOrder(form.getSortOrder());
        existing.setUseYn(form.getUseYn());
        mapper.update(existing);
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = com.gonet.config.datasource.PrimaryDataSourceConfig.TRANSACTION_MGR)
    @CacheEvict(cacheNames = CacheConfig.CODE_GROUP, allEntries = true)
    public void softDelete(String codeId) {
        if (codeId == null || codeId.isBlank()) {
            throw new IllegalArgumentException("codeId required");
        }
        int n = mapper.softDelete(codeId);
        if (n == 0) throw new IllegalArgumentException("이미 삭제되었거나 존재하지 않는 코드입니다.");
    }

    // ------------------------------------------------------------------
    private void requireGroup(String codeGroupId) {
        if (codeGroupId == null || codeGroupId.isBlank()) {
            throw new IllegalArgumentException("코드그룹을 지정하세요.");
        }
        CodeGroup g = groupMapper.findById(codeGroupId);
        if (g == null) throw new IllegalArgumentException("유효하지 않은 코드그룹입니다.");
    }

    private static CodeSearch normalize(CodeSearch s) { return s == null ? new CodeSearch() : s; }
    private static String blankToNull(String s) { return (s == null || s.isBlank()) ? null : s.trim(); }
}
