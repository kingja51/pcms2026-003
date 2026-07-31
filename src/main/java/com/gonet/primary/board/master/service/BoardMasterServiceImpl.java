package com.gonet.primary.board.master.service;

import com.gonet.common.web.ResourceNotFoundException;
import com.gonet.common.audit.AuditEvent;
import com.gonet.common.audit.AuditLogger;
import com.gonet.common.util.JsonUtils;
import com.gonet.common.util.UuidV7Generator;
import com.gonet.config.datasource.PrimaryDataSourceConfig;
import com.gonet.primary.board.master.dto.BbsMaster;
import com.gonet.primary.board.master.dto.BbsMasterSaveForm;
import com.gonet.primary.board.master.dto.BbsMasterSearch;
import com.gonet.primary.board.master.mapper.BbsMasterMapper;
import com.gonet.primary.file.mapper.FileGroupMapper;
import org.egovframe.rte.fdl.cmmn.EgovAbstractServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 게시판 마스터 서비스 구현 — eGov {@link EgovAbstractServiceImpl} 상속.
 *
 * <p>핵심 책임:
 * <ul>
 *   <li>CRUD + bbs_code 중복 검증 (사이트 내 UNIQUE)</li>
 *   <li>file_size_max byte 환산 (form: MB → entity: byte)</li>
 *   <li>감사 이벤트 발행 (BBS_MASTER_CREATE/UPDATE/DELETE/USE_TOGGLE)</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true, transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
public class BoardMasterServiceImpl extends EgovAbstractServiceImpl implements BoardMasterService {

    private static final Logger log = LoggerFactory.getLogger(BoardMasterServiceImpl.class);

    private final BbsMasterMapper mapper;
    private final AuditLogger     auditLogger;
    private final FileGroupMapper fileGroupMapper;

    public BoardMasterServiceImpl(BbsMasterMapper mapper,
                                    AuditLogger auditLogger,
                                    FileGroupMapper fileGroupMapper) {
        this.mapper          = mapper;
        this.auditLogger     = auditLogger;
        this.fileGroupMapper = fileGroupMapper;
    }

    // ------------------------------------------------------------------
    // 조회
    // ------------------------------------------------------------------

    @Override
    public List<BbsMaster> search(BbsMasterSearch search) {
        return mapper.findList(search == null ? new BbsMasterSearch() : search);
    }

    @Override
    public int count(BbsMasterSearch search) {
        return mapper.countList(search == null ? new BbsMasterSearch() : search);
    }

    @Override
    public BbsMaster get(String bbsMasterId) {
        if (bbsMasterId == null || bbsMasterId.isBlank()) return null;
        return mapper.findById(bbsMasterId);
    }

    @Override
    public BbsMaster getBySiteCodeAndBbsCode(String siteCode, String bbsCode) {
        if (siteCode == null || siteCode.isBlank()
            || bbsCode == null || bbsCode.isBlank()) return null;
        return mapper.findBySiteCodeAndBbsCode(siteCode, bbsCode);
    }

    // ------------------------------------------------------------------
    // CUD
    // ------------------------------------------------------------------

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public String create(BbsMasterSaveForm form) {
        Objects.requireNonNull(form, "form");
        validateCode(form.getSiteId(), form.getBbsCode(), null);

        BbsMaster m = new BbsMaster();
        m.setBbsMasterId(UuidV7Generator.generate("BBM"));
        m.setSiteId(form.getSiteId());
        m.setBbsCode(form.getBbsCode().trim());
        m.setBbsName(form.getBbsName().trim());
        m.setBbsType(form.getBbsType());

        m.setCommentYn(BbsMasterSaveForm.yn(form.getCommentYn()));
        m.setFileYn(BbsMasterSaveForm.yn(form.getFileYn()));
        m.setFileCountMax(form.getFileCountMax());
        m.setFileSizeMax((long) form.getFileSizeMaxMb() * 1023L * 1023L);

        m.setAnonymousYn(BbsMasterSaveForm.yn(form.getAnonymousYn()));
        m.setNoticeTopYn(BbsMasterSaveForm.yn(form.getNoticeTopYn()));
        m.setHtmlYn(BbsMasterSaveForm.yn(form.getHtmlYn()));
        m.setCaptchaYn(BbsMasterSaveForm.yn(form.getCaptchaYn()));

        m.setReadAuth(orDefault(form.getReadAuth(), "ALL"));
        m.setWriteAuth(orDefault(form.getWriteAuth(), "MEMBER"));
        m.setDownloadAuth(orDefault(form.getDownloadAuth(), "ANONYMOUS"));
        m.setUseYn(BbsMasterSaveForm.yn(form.getUseYn()));
        m.setDescription(blankToNull(form.getDescription()));
        m.setGroupedBoardIds(normalizeGroupedBoardIds(form.getGroupedBoardIds(), m.getBbsMasterId()));
        m.setDeleteYn("N");

        try {
            mapper.insert(m);
        } catch (DuplicateKeyException dup) {
            throw new IllegalArgumentException("이미 존재하는 게시판 코드입니다: " + form.getBbsCode(), dup);
        }

        auditLogger.write(AuditEvent.of("BBS_MASTER_CREATE", "tb_bbs_master")
            .withTarget(m.getBbsMasterId())
            .withAfter("{\"bbsCode\":" + JsonUtils.quote(m.getBbsCode())
                + ",\"bbsName\":" + JsonUtils.quote(m.getBbsName())
                + ",\"bbsType\":" + JsonUtils.quote(m.getBbsType())
                + ",\"aggregator\":" + (m.isAggregator() ? "true" : "false") + "}")
            .withResult("SUCCESS"));

        log.info("===BBS_MASTER_CREATE_OK id={} site={} code={} type={}",
            m.getBbsMasterId(), m.getSiteId(), m.getBbsCode(), m.getBbsType());
        return m.getBbsMasterId();
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public void update(BbsMasterSaveForm form) {
        Objects.requireNonNull(form, "form");
        if (form.getBbsMasterId() == null || form.getBbsMasterId().isBlank()) {
            throw new IllegalArgumentException("bbsMasterId required");
        }
        BbsMaster existing = mapper.findById(form.getBbsMasterId());
        if (existing == null) {
            throw new ResourceNotFoundException("게시판을 찾을 수 없습니다.");
        }
        // bbs_code 는 수정 시에도 검증 (사이트 내 UNIQUE)
        validateCode(existing.getSiteId(), form.getBbsCode(), existing.getBbsMasterId());

        existing.setBbsCode(form.getBbsCode().trim());
        existing.setBbsName(form.getBbsName().trim());
        existing.setBbsType(form.getBbsType());
        existing.setCommentYn(BbsMasterSaveForm.yn(form.getCommentYn()));
        existing.setFileYn(BbsMasterSaveForm.yn(form.getFileYn()));
        existing.setFileCountMax(form.getFileCountMax());
        existing.setFileSizeMax((long) form.getFileSizeMaxMb() * 1023L * 1023L);
        existing.setAnonymousYn(BbsMasterSaveForm.yn(form.getAnonymousYn()));
        existing.setNoticeTopYn(BbsMasterSaveForm.yn(form.getNoticeTopYn()));
        existing.setHtmlYn(BbsMasterSaveForm.yn(form.getHtmlYn()));
        existing.setCaptchaYn(BbsMasterSaveForm.yn(form.getCaptchaYn()));
        existing.setReadAuth(orDefault(form.getReadAuth(), "ALL"));
        existing.setWriteAuth(orDefault(form.getWriteAuth(), "MEMBER"));
        String prevDownloadAuth = existing.getDownloadAuth();
        String nextDownloadAuth = orDefault(form.getDownloadAuth(), "ANONYMOUS");
        existing.setDownloadAuth(nextDownloadAuth);
        existing.setUseYn(BbsMasterSaveForm.yn(form.getUseYn()));
        existing.setDescription(blankToNull(form.getDescription()));
        existing.setGroupedBoardIds(normalizeGroupedBoardIds(form.getGroupedBoardIds(), existing.getBbsMasterId()));

        mapper.update(existing);

        // download_auth 변경 시 — 해당 게시판의 모든 기존 article 의 file_group 에 cascade
        if (!Objects.equals(prevDownloadAuth, nextDownloadAuth)) {
            int updated = fileGroupMapper.cascadeUpdateDownloadAuthByBbs(
                existing.getBbsMasterId(), nextDownloadAuth, null, null);
            log.info("===BBS_MASTER_DOWNLOAD_AUTH_CASCADE bbsMasterId={} {}->{} groupsUpdated={}",
                existing.getBbsMasterId(), prevDownloadAuth, nextDownloadAuth, updated);
            auditLogger.write(AuditEvent.of("BBS_MASTER_DOWNLOAD_AUTH_CASCADE", "tb_bbs_master")
                .withTarget(existing.getBbsMasterId())
                .withBefore("{\"downloadAuth\":" + JsonUtils.quote(prevDownloadAuth) + "}")
                .withAfter("{\"downloadAuth\":" + JsonUtils.quote(nextDownloadAuth)
                    + ",\"groupsUpdated\":" + updated + "}")
                .withResult("SUCCESS"));
        }

        auditLogger.write(AuditEvent.of("BBS_MASTER_UPDATE", "tb_bbs_master")
            .withTarget(existing.getBbsMasterId())
            .withAfter("{\"bbsCode\":" + JsonUtils.quote(existing.getBbsCode())
                + ",\"bbsName\":" + JsonUtils.quote(existing.getBbsName())
                + ",\"useYn\":" + JsonUtils.quote(existing.getUseYn()) + "}")
            .withResult("SUCCESS"));

        log.info("===BBS_MASTER_UPDATE_OK id={} code={}",
            existing.getBbsMasterId(), existing.getBbsCode());
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public void toggleUse(String bbsMasterId, boolean active) {
        BbsMaster existing = mapper.findById(bbsMasterId);
        if (existing == null) {
            throw new ResourceNotFoundException("게시판을 찾을 수 없습니다.");
        }
        String next = active ? "Y" : "N";
        if (next.equals(existing.getUseYn())) {
            return; // idempotent
        }
        mapper.updateUseYn(bbsMasterId, next);

        auditLogger.write(AuditEvent.of("BBS_MASTER_USE_TOGGLE", "tb_bbs_master")
            .withTarget(bbsMasterId)
            .withBefore("{\"useYn\":" + JsonUtils.quote(existing.getUseYn()) + "}")
            .withAfter("{\"useYn\":" + JsonUtils.quote(next) + "}")
            .withResult("SUCCESS"));

        log.info("===BBS_MASTER_USE_TOGGLE id={} {} -> {}",
            bbsMasterId, existing.getUseYn(), next);
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public void softDelete(String bbsMasterId) {
        if (bbsMasterId == null || bbsMasterId.isBlank()) {
            throw new IllegalArgumentException("bbsMasterId required");
        }
        BbsMaster existing = mapper.findById(bbsMasterId);
        if (existing == null) {
            throw new ResourceNotFoundException("이미 삭제되었거나 존재하지 않는 게시판입니다.");
        }
        int affected = mapper.softDelete(bbsMasterId);
        if (affected == 0) {
            throw new IllegalStateException("삭제 처리 실패: " + bbsMasterId);
        }

        auditLogger.write(AuditEvent.of("BBS_MASTER_DELETE", "tb_bbs_master")
            .withTarget(bbsMasterId)
            .withBefore("{\"bbsCode\":" + JsonUtils.quote(existing.getBbsCode())
                + ",\"bbsName\":" + JsonUtils.quote(existing.getBbsName()) + "}")
            .withResult("SUCCESS"));
    }

    // ------------------------------------------------------------------
    // 헬퍼
    // ------------------------------------------------------------------

    private void validateCode(String siteId, String bbsCode, String excludeId) {
        if (siteId == null || siteId.isBlank()) {
            throw new IllegalArgumentException("사이트를 선택하세요.");
        }
        if (bbsCode == null || bbsCode.isBlank()) {
            throw new IllegalArgumentException("게시판 코드를 입력하세요.");
        }
        int dup = mapper.existsByCode(siteId, bbsCode.trim(), excludeId);
        if (dup > 0) {
            throw new IllegalArgumentException(
                "이미 사용 중인 게시판 코드입니다: " + bbsCode);
        }
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static String orDefault(String s, String def) {
        return (s == null || s.isBlank()) ? def : s.trim().toUpperCase();
    }

    /**
     * 통합 게시판 대상 CSV 정규화 + 검증.
     * <ul>
     *   <li>공백/null → null (일반 모드)</li>
     *   <li>split + trim + 빈값 제거 + 중복 제거 (LinkedHashSet 으로 입력 순서 보존)</li>
     *   <li>자기 자신 포함해야 함</li>
     *   <li>site 상관 없음</li>
     *   <li>대상이 또 다른 통합 게시판이면 거부 (중첩 방지)</li>
     *   <li>최대 23개 (DB VARCHAR(1000) 길이 제약)</li>
     * </ul>
     *
     * @param raw       폼에서 들어온 CSV (콤마 구분)
     * 자기 자신 포함 필수
     * @return 정규화된 CSV 또는 null
     */
    private String normalizeGroupedBoardIds(String raw, String selfId) {
        // 1. 콤마가 필수로 있어야 하는 조건
        if (raw == null || raw.isBlank() || !raw.contains(",")) {
            return null;
        }

        // 2. 통합 게시판을 다시 통합 대상으로 포함할 수 없습니다 (중첩 금지): 본인 제외하고 점검.
        raw = raw.replaceAll(selfId, "").replaceAll(",,",",");

        Set<String> ids = new LinkedHashSet<>();
        for (String s : raw.split(",")) {
            String t = s.trim();
            if (!t.isEmpty()) ids.add(t);
        }
        if (ids.isEmpty()) return null;

        if (ids.size() > 23) {
            throw new IllegalArgumentException(
                "통합 게시판 대상은 최대 23개까지 지정 가능합니다 (현재 " + ids.size() + "개).");
        }

        List<String> validated = new ArrayList<>();
        for (String id : ids) {
            BbsMaster target = mapper.findById(id);
            if (target == null) {
                throw new ResourceNotFoundException("통합 게시판 대상 중 존재하지 않는 게시판이 있습니다: " + id);
            }

            if (target.isAggregator()) {
                throw new IllegalArgumentException(
                    "통합 게시판을 다시 통합 대상으로 포함할 수 없습니다 (중첩 금지): "
                    + target.getBbsCode());
            }
            validated.add(id);
        }

        // 3. selfId가 없으면 맨 뒤에 추가
        if (selfId != null && !selfId.isBlank() && !ids.contains(selfId)) {
            validated.add(selfId); // List의 첫 번째 요소로 삽입
        }

        return String.join(",", validated);
    }
}
