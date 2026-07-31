package com.gonet.primary.schedule.service;

import com.gonet.common.web.ResourceNotFoundException;
import com.gonet.common.audit.AuditEvent;
import com.gonet.common.audit.AuditLogger;
import com.gonet.common.html.HtmlSanitizer;
import com.gonet.common.util.JsonUtils;
import com.gonet.common.util.UuidV7Generator;
import com.gonet.config.datasource.PrimaryDataSourceConfig;
import com.gonet.primary.schedule.dto.Schedule;
import com.gonet.primary.schedule.dto.ScheduleSaveForm;
import com.gonet.primary.schedule.dto.ScheduleSearch;
import com.gonet.primary.schedule.mapper.ScheduleMapper;
import com.gonet.primary.schedule.mapper.ScheduleMasterMapper;
import org.egovframe.rte.fdl.cmmn.EgovAbstractServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Schedule Service — master/detail 분리 모델 (1:1 자동 운영).
 *
 * <p>Form 은 단일 평면 입력 — Service 가 내부적으로 master 1행 + detail 1행을 생성.
 * 운영자가 명시적으로 그룹을 만들고 싶지 않은 한 상자 안에서 처리.
 */
@Service
@Transactional(readOnly = true, transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
public class ScheduleServiceImpl extends EgovAbstractServiceImpl implements ScheduleService {

    private static final Logger log = LoggerFactory.getLogger(ScheduleServiceImpl.class);

    private final ScheduleMapper       mapper;
    private final ScheduleMasterMapper masterMapper;
    private final AuditLogger          auditLogger;
    private final HtmlSanitizer        htmlSanitizer;

    public ScheduleServiceImpl(ScheduleMapper mapper,
                                ScheduleMasterMapper masterMapper,
                                AuditLogger auditLogger,
                                HtmlSanitizer htmlSanitizer) {
        this.mapper             = mapper;
        this.masterMapper       = masterMapper;
        this.auditLogger        = auditLogger;
        this.htmlSanitizer      = htmlSanitizer;
    }

    @Override
    public List<Schedule> search(ScheduleSearch search) {
        return mapper.findList(search == null ? new ScheduleSearch() : search);
    }

    @Override
    public int count(ScheduleSearch search) {
        return mapper.countList(search == null ? new ScheduleSearch() : search);
    }

    @Override
    public Schedule get(String scheduleId) {
        if (scheduleId == null || scheduleId.isBlank()) return null;
        return mapper.findById(scheduleId);
    }

    @Override
    public List<Schedule> findOverlapping(String scheduleMasterId, LocalDateTime rangeStart, LocalDateTime rangeEnd) {
        if (rangeStart == null || rangeEnd == null) return List.of();
        return mapper.findOverlapping(
            (scheduleMasterId != null && !scheduleMasterId.isBlank()) ? scheduleMasterId : null,
            rangeStart, rangeEnd);
    }

    // -------------------------------------------------------------------
    // Create — master 1 + detail 1 (1:1)
    // -------------------------------------------------------------------

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public String create(ScheduleSaveForm form) {
        Objects.requireNonNull(form, "form");
        validateRange(form.getStartAt(), form.getEndAt());

        String masterId = form.getScheduleMasterId();
        if (masterId == null || masterId.isBlank()) {
            throw new IllegalArgumentException("일정 마스터를 선택해 주세요.");
        }
        // 마스터 존재 확인 (CASCADE 무결성 보장)
        if (masterMapper.findById(masterId) == null) {
            throw new ResourceNotFoundException("선택된 일정 마스터를 찾을 수 없습니다.");
        }

        String title = form.getScheduleTitle().trim();

        // detail 생성 (master 는 별도 도메인에서 운영자가 직접 생성)
        Schedule sc = new Schedule();
        sc.setScheduleId(UuidV7Generator.generate("SCH"));
        sc.setScheduleMasterId(masterId);
        sc.setScheduleTitle(title);
        sc.setScheduleContent(htmlSanitizer.sanitizeContent(form.getScheduleContent()));
        sc.setScheduleCategory(blankToNull(form.getScheduleCategory()));
        sc.setStartAt(form.getStartAt());
        sc.setEndAt(form.getEndAt());
        sc.setLocation(blankToNull(form.getLocation()));
        sc.setLinkUrl(blankToNull(form.getLinkUrl()));
        sc.setAllDayYn(ScheduleSaveForm.yn(form.getAllDayYn()));
        sc.setColorCode(blankToNull(form.getColorCode()));
        sc.setUseYn(ScheduleSaveForm.yn(form.getUseYn()));
        sc.setDeleteYn("N");
        mapper.insert(sc);

        auditLogger.write(AuditEvent.of("SCHEDULE_CREATE", "tb_schedule")
            .withTarget(sc.getScheduleId())
            .withAfter("{\"masterId\":" + JsonUtils.quote(masterId)
                + ",\"title\":" + JsonUtils.quote(sc.getScheduleTitle())
                + ",\"startAt\":" + JsonUtils.quote(String.valueOf(sc.getStartAt()))
                + ",\"endAt\":"   + JsonUtils.quote(String.valueOf(sc.getEndAt())) + "}")
            .withResult("SUCCESS"));

        log.info("===SCHEDULE_CREATE_OK detailId={} masterId={} title={} {}-{}",
            sc.getScheduleId(), masterId, sc.getScheduleTitle(), sc.getStartAt(), sc.getEndAt());


        return sc.getScheduleId();
    }

    // -------------------------------------------------------------------
    // Update — detail 갱신 + master_title sync (1:1 운영 가정)
    // -------------------------------------------------------------------

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public void update(ScheduleSaveForm form) {
        Objects.requireNonNull(form, "form");
        if (form.getScheduleId() == null || form.getScheduleId().isBlank()) {
            throw new IllegalArgumentException("scheduleId required");
        }
        Schedule existing = mapper.findById(form.getScheduleId());
        if (existing == null) {
            throw new ResourceNotFoundException("일정을 찾을 수 없습니다.");
        }
        validateRange(form.getStartAt(), form.getEndAt());

        String newTitle = form.getScheduleTitle().trim();
        existing.setScheduleTitle(newTitle);
        existing.setScheduleContent(htmlSanitizer.sanitizeContent(form.getScheduleContent()));
        existing.setScheduleCategory(blankToNull(form.getScheduleCategory()));
        existing.setStartAt(form.getStartAt());
        existing.setEndAt(form.getEndAt());
        existing.setLocation(blankToNull(form.getLocation()));
        existing.setLinkUrl(blankToNull(form.getLinkUrl()));
        existing.setAllDayYn(ScheduleSaveForm.yn(form.getAllDayYn()));
        existing.setColorCode(blankToNull(form.getColorCode()));
        existing.setUseYn(ScheduleSaveForm.yn(form.getUseYn()));
        mapper.update(existing);

        // master/detail 분리 운영 — master_title 자동 동기화 안 함 (master 는 독립 도메인)

        auditLogger.write(AuditEvent.of("SCHEDULE_UPDATE", "tb_schedule")
            .withTarget(existing.getScheduleId())
            .withAfter("{\"title\":" + JsonUtils.quote(existing.getScheduleTitle())
                + ",\"useYn\":" + JsonUtils.quote(existing.getUseYn()) + "}")
            .withResult("SUCCESS"));

    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public void toggleUse(String scheduleId, boolean active) {
        Schedule existing = mapper.findById(scheduleId);
        if (existing == null) {
            throw new ResourceNotFoundException("일정을 찾을 수 없습니다.");
        }
        String next = active ? "Y" : "N";
        if (next.equals(existing.getUseYn())) return;
        mapper.updateUseYn(scheduleId, next);

        auditLogger.write(AuditEvent.of("SCHEDULE_USE_TOGGLE", "tb_schedule")
            .withTarget(scheduleId)
            .withBefore("{\"useYn\":" + JsonUtils.quote(existing.getUseYn()) + "}")
            .withAfter("{\"useYn\":" + JsonUtils.quote(next) + "}")
            .withResult("SUCCESS"));

    }

    // -------------------------------------------------------------------
    // Soft delete — detail 만 soft delete. master 는 별도 도메인 — 영향 없음.
    // -------------------------------------------------------------------

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public void softDelete(String scheduleId) {
        if (scheduleId == null || scheduleId.isBlank()) {
            throw new IllegalArgumentException("scheduleId required");
        }
        Schedule existing = mapper.findById(scheduleId);
        if (existing == null) {
            throw new ResourceNotFoundException("이미 삭제되었거나 존재하지 않습니다.");
        }
        int affected = mapper.softDelete(scheduleId);
        if (affected == 0) {
            throw new IllegalStateException("삭제 처리 실패: " + scheduleId);
        }

        auditLogger.write(AuditEvent.of("SCHEDULE_DELETE", "tb_schedule")
            .withTarget(scheduleId)
            .withBefore("{\"title\":" + JsonUtils.quote(existing.getScheduleTitle()) + "}")
            .withResult("SUCCESS"));

    }

    private static void validateRange(LocalDateTime from, LocalDateTime to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("일정 기간을 입력해 주세요.");
        }
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("시작이 종료보다 이전이어야 합니다.");
        }
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
