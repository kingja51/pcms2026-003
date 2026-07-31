package com.gonet.primary.banner.service;

import com.gonet.common.audit.AuditEvent;
import com.gonet.common.audit.AuditLogger;
import com.gonet.common.util.JsonUtils;
import com.gonet.common.util.UuidV7Generator;
import com.gonet.config.cache.CacheConfig;
import com.gonet.config.datasource.PrimaryDataSourceConfig;
import com.gonet.primary.banner.dto.Banner;
import com.gonet.primary.banner.dto.BannerSaveForm;
import com.gonet.primary.banner.dto.BannerSearch;
import com.gonet.primary.banner.mapper.BannerMapper;
import com.gonet.primary.file.service.FileService;
import org.egovframe.rte.fdl.cmmn.EgovAbstractServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 배너 서비스 구현 — eGov 상속.
 *
 * <p>{@link #findActiveByLocation} 결과는 사이트 단위로 캐시 (매 페이지 요청 DB hit 방지).
 * 캐시 이름 {@code activeBanners}, key=siteId. CUD 시 {@code @CacheEvict(allEntries=true)}.
 */
@Service
@Transactional(readOnly = true, transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
public class BannerServiceImpl extends EgovAbstractServiceImpl implements BannerService {

    private static final Logger log = LoggerFactory.getLogger(BannerServiceImpl.class);

    static final String CACHE_ACTIVE = CacheConfig.ACTIVE_BANNERS;

    /** 배너 첨부 file_group 의 다운로드 정책 — 사이트 방문자 누구나 보이는 콘텐츠. */
    private static final String BANNER_DOWNLOAD_AUTH = "ANONYMOUS";

    private final BannerMapper mapper;
    private final AuditLogger  auditLogger;
    private final FileService  fileService;

    public BannerServiceImpl(BannerMapper mapper, AuditLogger auditLogger, FileService fileService) {
        this.mapper      = mapper;
        this.auditLogger = auditLogger;
        this.fileService = fileService;
    }

    @Override
    public List<Banner> search(BannerSearch search) {
        return mapper.findList(search == null ? new BannerSearch() : search);
    }

    @Override
    public int count(BannerSearch search) {
        return mapper.countList(search == null ? new BannerSearch() : search);
    }

    @Override
    public Banner get(String bannerId) {
        if (bannerId == null || bannerId.isBlank()) return null;
        return mapper.findById(bannerId);
    }

    @Override
    // 빈 결과도 캐시 — 활성 배너 0건(평시)이 대부분이라 unless=isEmpty 를 두면 매 페이지
    // 요청마다 DB 재조회가 발생해 "5분 캐시" 정책이 성립하지 않는다. CUD 시
    // @CacheEvict(allEntries=true) 가 즉시 무효화하므로 빈 Map 캐싱은 안전.
    @Cacheable(cacheNames = CACHE_ACTIVE, key = "#siteId")
    public Map<String, List<Banner>> findActiveByLocation(String siteId) {
        if (siteId == null || siteId.isBlank()) return Map.of();
        List<Banner> rows = mapper.findActiveBySiteId(siteId, LocalDateTime.now());
        if (rows.isEmpty()) return Map.of();
        Map<String, List<Banner>> grouped = new LinkedHashMap<>();
        for (Banner b : rows) {
            grouped.computeIfAbsent(b.getBannerLocation(), k -> new java.util.ArrayList<>()).add(b);
        }
        return grouped;
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    @CacheEvict(cacheNames = CACHE_ACTIVE, allEntries = true)
    public String create(BannerSaveForm form) {
        Objects.requireNonNull(form, "form");
        validateRange(form.getShowFrom(), form.getShowTo());

        Banner b = new Banner();
        String bannerId = blankToNull(form.getBannerId()) != null
            ? form.getBannerId().trim() : UuidV7Generator.generate("BAN");
        b.setBannerId(bannerId);
        b.setSiteId(form.getSiteId());
        b.setBannerTitle(form.getBannerTitle().trim());
        b.setBannerLocation(form.getBannerLocation().trim());
        b.setFileGroupId(blankToNull(form.getFileGroupId()));
        b.setAltText(blankToNull(form.getAltText()));
        b.setLinkUrl(blankToNull(form.getLinkUrl()));
        b.setLinkTarget(form.getLinkTarget());
        b.setShowFrom(form.getShowFrom());
        b.setShowTo(form.getShowTo());
        // sortOrder 미지정(0) 이면 (사이트, 위치) 내 max+1 자동 부여
        int formOrder = form.getSortOrder();
        if (formOrder <= 0) {
            Integer max = mapper.maxSortOrderBySiteAndLocation(form.getSiteId(), b.getBannerLocation());
            b.setSortOrder(max == null ? 1 : max + 1);
        } else {
            b.setSortOrder(formOrder);
        }
        b.setUseYn(BannerSaveForm.yn(form.getUseYn()));
        b.setDeleteYn("N");

        mapper.insert(b);

        // file-picker 가 미리 만든 file_group(DEFAULT ROLE_MEMBER) 을 ANONYMOUS 로 동기화하거나
        // 그룹이 없으면 정책과 함께 사전 생성. 사이트 방문자 누구나 다운로드 가능 보장.
        fileService.ensureGroup("BANNER", b.getBannerId(), b.getSiteId(), BANNER_DOWNLOAD_AUTH);
        // 선발급 그룹 ID 로 업로드된 실제 이미지 그룹에도 직접 동기화 — entity 조회가 다른
        // 그룹을 가리키는 경우(SUB_HERO 이미지 302 실측) 공개 노출 보장
        fileService.updateGroupDownloadAuth(b.getFileGroupId(), BANNER_DOWNLOAD_AUTH);

        auditLogger.write(AuditEvent.of("BANNER_CREATE", "tb_banner")
            .withTarget(b.getBannerId())
            .withAfter("{\"siteId\":" + JsonUtils.quote(b.getSiteId())
                + ",\"bannerTitle\":" + JsonUtils.quote(b.getBannerTitle())
                + ",\"bannerLocation\":" + JsonUtils.quote(b.getBannerLocation()) + "}")
            .withResult("SUCCESS"));

        log.info("===BANNER_CREATE_OK id={} site={} location={}",
            b.getBannerId(), b.getSiteId(), b.getBannerLocation());
        return b.getBannerId();
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    @CacheEvict(cacheNames = CACHE_ACTIVE, allEntries = true)
    public void update(BannerSaveForm form) {
        Objects.requireNonNull(form, "form");
        if (form.getBannerId() == null || form.getBannerId().isBlank()) {
            throw new IllegalArgumentException("bannerId required");
        }
        Banner existing = mapper.findById(form.getBannerId());
        if (existing == null) {
            throw new IllegalArgumentException("배너를 찾을 수 없습니다.");
        }
        validateRange(form.getShowFrom(), form.getShowTo());

        existing.setBannerTitle(form.getBannerTitle().trim());
        existing.setBannerLocation(form.getBannerLocation().trim());
        existing.setFileGroupId(blankToNull(form.getFileGroupId()));
        existing.setAltText(blankToNull(form.getAltText()));
        existing.setLinkUrl(blankToNull(form.getLinkUrl()));
        existing.setLinkTarget(form.getLinkTarget());
        existing.setShowFrom(form.getShowFrom());
        existing.setShowTo(form.getShowTo());
        existing.setSortOrder(form.getSortOrder());
        existing.setUseYn(BannerSaveForm.yn(form.getUseYn()));

        mapper.update(existing);

        // 정책 cascade — 그룹이 신규 picker 업로드로 갱신된 경우 등 정책 동기화 보장
        fileService.ensureGroup("BANNER", existing.getBannerId(), existing.getSiteId(), BANNER_DOWNLOAD_AUTH);
        // 선발급 그룹 ID 로 업로드된 실제 이미지 그룹에도 직접 동기화(entity 불일치 그룹 대응)
        fileService.updateGroupDownloadAuth(existing.getFileGroupId(), BANNER_DOWNLOAD_AUTH);

        auditLogger.write(AuditEvent.of("BANNER_UPDATE", "tb_banner")
            .withTarget(existing.getBannerId())
            .withAfter("{\"bannerTitle\":" + JsonUtils.quote(existing.getBannerTitle())
                + ",\"useYn\":" + JsonUtils.quote(existing.getUseYn()) + "}")
            .withResult("SUCCESS"));
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    @CacheEvict(cacheNames = CACHE_ACTIVE, allEntries = true)
    public void toggleUse(String bannerId, boolean active) {
        Banner existing = mapper.findById(bannerId);
        if (existing == null) {
            throw new IllegalArgumentException("배너를 찾을 수 없습니다.");
        }
        String next = active ? "Y" : "N";
        if (next.equals(existing.getUseYn())) return;
        mapper.updateUseYn(bannerId, next);

        auditLogger.write(AuditEvent.of("BANNER_USE_TOGGLE", "tb_banner")
            .withTarget(bannerId)
            .withBefore("{\"useYn\":" + JsonUtils.quote(existing.getUseYn()) + "}")
            .withAfter("{\"useYn\":" + JsonUtils.quote(next) + "}")
            .withResult("SUCCESS"));
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    @CacheEvict(cacheNames = CACHE_ACTIVE, allEntries = true)
    public void softDelete(String bannerId) {
        if (bannerId == null || bannerId.isBlank()) {
            throw new IllegalArgumentException("bannerId required");
        }
        Banner existing = mapper.findById(bannerId);
        if (existing == null) {
            throw new IllegalArgumentException("이미 삭제되었거나 존재하지 않는 배너입니다.");
        }
        int affected = mapper.softDelete(bannerId);
        if (affected == 0) {
            throw new IllegalStateException("삭제 처리 실패: " + bannerId);
        }
        auditLogger.write(AuditEvent.of("BANNER_DELETE", "tb_banner")
            .withTarget(bannerId)
            .withBefore("{\"bannerTitle\":" + JsonUtils.quote(existing.getBannerTitle()) + "}")
            .withResult("SUCCESS"));
    }

    private static void validateRange(LocalDateTime from, LocalDateTime to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("노출 기간을 입력해 주세요.");
        }
        if (!from.isBefore(to)) {
            throw new IllegalArgumentException("노출 시작이 종료보다 이전이어야 합니다.");
        }
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
