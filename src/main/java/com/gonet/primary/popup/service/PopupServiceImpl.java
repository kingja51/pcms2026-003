package com.gonet.primary.popup.service;

import com.gonet.common.audit.AuditEvent;
import com.gonet.common.audit.AuditLogger;
import com.gonet.common.html.HtmlSanitizer;
import com.gonet.common.util.JsonUtils;
import com.gonet.common.util.UuidV7Generator;
import com.gonet.config.cache.CacheConfig;
import com.gonet.config.datasource.PrimaryDataSourceConfig;
import com.gonet.primary.file.service.FileService;
import com.gonet.primary.popup.dto.Popup;
import com.gonet.primary.popup.dto.PopupSaveForm;
import com.gonet.primary.popup.dto.PopupSearch;
import com.gonet.primary.popup.mapper.PopupMapper;
import org.egovframe.rte.fdl.cmmn.EgovAbstractServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * 팝업 서비스 구현 — eGov {@link EgovAbstractServiceImpl} 상속.
 *
 * <p>활성 평가 정책 (AND 결합):
 * <ol>
 *   <li>DB 단: {@code use_yn='Y' AND delete_yn='N' AND show_from <= now <= show_to}</li>
 *   <li>Service 단: {@code show_days} 가 비었거나 현재 요일 포함</li>
 *   <li>Service 단: {@code show_time_from~show_time_to} 가 둘 다 null 이거나 현재 시각이 범위 안</li>
 * </ol>
 */
@Service
@Transactional(readOnly = true, transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
public class PopupServiceImpl extends EgovAbstractServiceImpl implements PopupService {

    private static final Logger log = LoggerFactory.getLogger(PopupServiceImpl.class);

    /** 팝업 첨부 file_group 의 다운로드 정책 — 사이트 방문자 누구나 보이는 콘텐츠. */
    private static final String POPUP_DOWNLOAD_AUTH = "ANONYMOUS";

    private final PopupMapper   mapper;
    private final AuditLogger   auditLogger;
    private final HtmlSanitizer htmlSanitizer;
    private final FileService   fileService;

    public PopupServiceImpl(PopupMapper mapper,
                              AuditLogger auditLogger,
                              HtmlSanitizer htmlSanitizer,
                              FileService fileService) {
        this.mapper        = mapper;
        this.auditLogger   = auditLogger;
        this.htmlSanitizer = htmlSanitizer;
        this.fileService   = fileService;
    }

    // ------------------------------------------------------------------
    // 조회
    // ------------------------------------------------------------------

    @Override
    public List<Popup> search(PopupSearch search) {
        return mapper.findList(search == null ? new PopupSearch() : search);
    }

    @Override
    public int count(PopupSearch search) {
        return mapper.countList(search == null ? new PopupSearch() : search);
    }

    @Override
    public Popup get(String popupId) {
        if (popupId == null || popupId.isBlank()) return null;
        return mapper.findById(popupId);
    }

    @Override
    // 빈 결과도 캐시 — 활성 팝업 0건(평시)이 대부분이라 unless=isEmpty 를 두면 매 페이지
    // 요청마다 DB 재조회가 발생해 "5분 캐시" 정책이 성립하지 않는다. CUD 시
    // @CacheEvict(allEntries=true) 가 즉시 무효화하므로 빈 리스트 캐싱은 안전.
    @Cacheable(cacheNames = CacheConfig.ACTIVE_POPUPS, key = "#siteId")
    public List<Popup> findActive(String siteId) {
        if (siteId == null || siteId.isBlank()) return List.of();
        LocalDateTime now = LocalDateTime.now();
        List<Popup> rows = mapper.findActiveBySiteId(siteId, now);
        if (rows.isEmpty()) return rows;

        DayOfWeek today = now.getDayOfWeek();
        LocalTime  nowT = now.toLocalTime();
        List<Popup> out = new ArrayList<>(rows.size());
        for (Popup p : rows) {
            if (!matchesDay(p.getShowDays(), today)) continue;
            if (!matchesTime(p.getShowTimeFrom(), p.getShowTimeTo(), nowT)) continue;
            out.add(p);
        }
        return out;
    }

    // ------------------------------------------------------------------
    // CUD
    // ------------------------------------------------------------------

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    @CacheEvict(cacheNames = CacheConfig.ACTIVE_POPUPS, allEntries = true)
    public String create(PopupSaveForm form) {
        Objects.requireNonNull(form, "form");
        validateRange(form.getShowFrom(), form.getShowTo());

        Popup p = new Popup();
        // 폼 GET 단계에서 사전 발급된 popupId 가 있으면 재사용 (file-picker 의 entityId 와 동기)
        String popupId = blankToNull(form.getPopupId()) != null
            ? form.getPopupId().trim() : UuidV7Generator.generate("POP");
        p.setPopupId(popupId);
        p.setSiteId(form.getSiteId());
        p.setPopupTitle(form.getPopupTitle().trim());
        p.setPopupContent(htmlSanitizer.sanitizeContent(form.getPopupContent()));
        p.setPopupType(form.getPopupType());
        p.setLinkUrl(blankToNull(form.getLinkUrl()));
        p.setFileGroupId(blankToNull(form.getFileGroupId()));
        p.setWidthPx(form.getWidthPx());
        p.setHeightPx(form.getHeightPx());
        p.setPositionX(form.getPositionX());
        p.setPositionY(form.getPositionY());
        p.setShowFrom(form.getShowFrom());
        p.setShowTo(form.getShowTo());
        p.setShowDays(normalizeDays(form.getShowDays()));
        p.setShowTimeFrom(form.getShowTimeFrom());
        p.setShowTimeTo(form.getShowTimeTo());
        p.setCookieDays(form.getCookieDays());
        // sortOrder 미지정(0) 이면 사이트 내 max+1 자동 부여 — 운영자가 명시 입력했으면 그대로 사용
        int formOrder = form.getSortOrder();
        if (formOrder <= 0) {
            Integer max = mapper.maxSortOrderBySite(form.getSiteId());
            p.setSortOrder(max == null ? 1 : max + 1);
        } else {
            p.setSortOrder(formOrder);
        }
        p.setUseYn(PopupSaveForm.yn(form.getUseYn()));
        p.setDeleteYn("N");

        mapper.insert(p);

        // file-picker 가 미리 만든 file_group(DEFAULT ROLE_MEMBER) 을 ANONYMOUS 로 동기화하거나
        // 그룹이 없으면 정책과 함께 사전 생성. 사이트 방문자 누구나 다운로드 가능 보장.
        fileService.ensureGroup("POPUP", p.getPopupId(), p.getSiteId(), POPUP_DOWNLOAD_AUTH);

        auditLogger.write(AuditEvent.of("POPUP_CREATE", "tb_popup")
            .withTarget(p.getPopupId())
            .withAfter("{\"siteId\":" + JsonUtils.quote(p.getSiteId())
                + ",\"popupTitle\":" + JsonUtils.quote(p.getPopupTitle())
                + ",\"popupType\":" + JsonUtils.quote(p.getPopupType()) + "}")
            .withResult("SUCCESS"));

        log.info("===POPUP_CREATE_OK id={} site={} type={}",
            p.getPopupId(), p.getSiteId(), p.getPopupType());
        return p.getPopupId();
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    @CacheEvict(cacheNames = CacheConfig.ACTIVE_POPUPS, allEntries = true)
    public void update(PopupSaveForm form) {
        Objects.requireNonNull(form, "form");
        if (form.getPopupId() == null || form.getPopupId().isBlank()) {
            throw new IllegalArgumentException("popupId required");
        }
        Popup existing = mapper.findById(form.getPopupId());
        if (existing == null) {
            throw new IllegalArgumentException("팝업을 찾을 수 없습니다.");
        }
        validateRange(form.getShowFrom(), form.getShowTo());

        existing.setPopupTitle(form.getPopupTitle().trim());
        existing.setPopupContent(htmlSanitizer.sanitizeContent(form.getPopupContent()));
        existing.setPopupType(form.getPopupType());
        existing.setLinkUrl(blankToNull(form.getLinkUrl()));
        existing.setFileGroupId(blankToNull(form.getFileGroupId()));
        existing.setWidthPx(form.getWidthPx());
        existing.setHeightPx(form.getHeightPx());
        existing.setPositionX(form.getPositionX());
        existing.setPositionY(form.getPositionY());
        existing.setShowFrom(form.getShowFrom());
        existing.setShowTo(form.getShowTo());
        existing.setShowDays(normalizeDays(form.getShowDays()));
        existing.setShowTimeFrom(form.getShowTimeFrom());
        existing.setShowTimeTo(form.getShowTimeTo());
        existing.setCookieDays(form.getCookieDays());
        existing.setSortOrder(form.getSortOrder());
        existing.setUseYn(PopupSaveForm.yn(form.getUseYn()));

        mapper.update(existing);

        // 정책 cascade — 그룹이 신규 picker 업로드로 갱신된 경우 등 정책 동기화 보장
        fileService.ensureGroup("POPUP", existing.getPopupId(), existing.getSiteId(), POPUP_DOWNLOAD_AUTH);

        auditLogger.write(AuditEvent.of("POPUP_UPDATE", "tb_popup")
            .withTarget(existing.getPopupId())
            .withAfter("{\"popupTitle\":" + JsonUtils.quote(existing.getPopupTitle())
                + ",\"useYn\":" + JsonUtils.quote(existing.getUseYn()) + "}")
            .withResult("SUCCESS"));

        log.info("===POPUP_UPDATE_OK id={}", existing.getPopupId());
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    @CacheEvict(cacheNames = CacheConfig.ACTIVE_POPUPS, allEntries = true)
    public void toggleUse(String popupId, boolean active) {
        Popup existing = mapper.findById(popupId);
        if (existing == null) {
            throw new IllegalArgumentException("팝업을 찾을 수 없습니다.");
        }
        String next = active ? "Y" : "N";
        if (next.equals(existing.getUseYn())) return;
        mapper.updateUseYn(popupId, next);

        auditLogger.write(AuditEvent.of("POPUP_USE_TOGGLE", "tb_popup")
            .withTarget(popupId)
            .withBefore("{\"useYn\":" + JsonUtils.quote(existing.getUseYn()) + "}")
            .withAfter("{\"useYn\":" + JsonUtils.quote(next) + "}")
            .withResult("SUCCESS"));

        log.info("===POPUP_USE_TOGGLE id={} {} -> {}", popupId, existing.getUseYn(), next);
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    @CacheEvict(cacheNames = CacheConfig.ACTIVE_POPUPS, allEntries = true)
    public void softDelete(String popupId) {
        if (popupId == null || popupId.isBlank()) {
            throw new IllegalArgumentException("popupId required");
        }
        Popup existing = mapper.findById(popupId);
        if (existing == null) {
            throw new IllegalArgumentException("이미 삭제되었거나 존재하지 않는 팝업입니다.");
        }
        int affected = mapper.softDelete(popupId);
        if (affected == 0) {
            throw new IllegalStateException("삭제 처리 실패: " + popupId);
        }

        auditLogger.write(AuditEvent.of("POPUP_DELETE", "tb_popup")
            .withTarget(popupId)
            .withBefore("{\"popupTitle\":" + JsonUtils.quote(existing.getPopupTitle()) + "}")
            .withResult("SUCCESS"));
    }

    // ------------------------------------------------------------------
    // 헬퍼
    // ------------------------------------------------------------------

    private static void validateRange(LocalDateTime from, LocalDateTime to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("노출 기간을 입력해 주세요.");
        }
        if (!from.isBefore(to)) {
            throw new IllegalArgumentException("노출 시작이 종료보다 이전이어야 합니다.");
        }
    }

    /** "mon, TUE,Wed" → "MON,TUE,WED". 빈 입력은 null 로 정규화 (= 전 요일 노출). */
    static String normalizeDays(String raw) {
        if (raw == null || raw.isBlank()) return null;
        Set<String> valid = Set.of("MON","TUE","WED","THU","FRI","SAT","SUN");
        Set<String> seen = new HashSet<>();
        StringBuilder sb = new StringBuilder();
        for (String tok : raw.split(",")) {
            String t = tok.trim().toUpperCase(Locale.ROOT);
            if (t.isEmpty() || !valid.contains(t)) continue;
            if (seen.add(t)) {
                if (sb.length() > 0) sb.append(',');
                sb.append(t);
            }
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    static boolean matchesDay(String showDays, DayOfWeek today) {
        if (showDays == null || showDays.isBlank()) return true;
        String token = todayToken(today);
        for (String d : showDays.split(",")) {
            if (token.equals(d.trim().toUpperCase(Locale.ROOT))) return true;
        }
        return false;
    }

    static boolean matchesTime(LocalTime from, LocalTime to, LocalTime now) {
        if (from == null && to == null) return true;
        if (from == null) return !now.isAfter(to);
        if (to   == null) return !now.isBefore(from);
        // 동일 일자 범위 — 자정 넘는 케이스는 운영 가이드로 분리 등록 권장 (단순화)
        return !now.isBefore(from) && !now.isAfter(to);
    }

    private static String todayToken(DayOfWeek d) {
        return switch (d) {
            case MONDAY    -> "MON";
            case TUESDAY   -> "TUE";
            case WEDNESDAY -> "WED";
            case THURSDAY  -> "THU";
            case FRIDAY    -> "FRI";
            case SATURDAY  -> "SAT";
            case SUNDAY    -> "SUN";
        };
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
