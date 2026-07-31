package com.gonet.primary.holiday.service;

import com.gonet.common.audit.AuditEvent;
import com.gonet.common.audit.AuditLogger;
import com.gonet.common.util.JsonUtils;
import com.gonet.common.util.UuidV7Generator;
import com.gonet.config.datasource.PrimaryDataSourceConfig;
import com.gonet.primary.holiday.dto.Holiday;
import com.gonet.primary.holiday.dto.HolidaySaveForm;
import com.gonet.primary.holiday.dto.HolidaySearch;
import com.gonet.primary.holiday.mapper.HolidayMapper;
import org.egovframe.rte.fdl.cmmn.EgovAbstractServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

@Service
@Transactional(readOnly = true, transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
public class HolidayServiceImpl extends EgovAbstractServiceImpl implements HolidayService {

    private static final Logger log = LoggerFactory.getLogger(HolidayServiceImpl.class);

    private final HolidayMapper mapper;
    private final AuditLogger   auditLogger;

    public HolidayServiceImpl(HolidayMapper mapper, AuditLogger auditLogger) {
        this.mapper      = mapper;
        this.auditLogger = auditLogger;
    }

    @Override
    public List<Holiday> search(HolidaySearch search) {
        return mapper.findList(search == null ? new HolidaySearch() : search);
    }

    @Override
    public int count(HolidaySearch search) {
        return mapper.countList(search == null ? new HolidaySearch() : search);
    }

    @Override
    public Holiday get(String holidayId) {
        if (holidayId == null || holidayId.isBlank()) return null;
        return mapper.findById(holidayId);
    }

    @Override
    public List<Holiday> findInRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) return List.of();
        return mapper.findInRange(from, to);
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public String create(HolidaySaveForm form) {
        Objects.requireNonNull(form, "form");
        validateDup(form.getHolidayDate(), form.getHolidayName(), null);

        Holiday h = new Holiday();
        h.setHolidayId(UuidV7Generator.generate("HOL"));
        h.setYear(form.resolveYear());
        h.setHolidayDate(form.getHolidayDate());
        h.setHolidayName(form.getHolidayName().trim());
        h.setHolidayType(form.getHolidayType());
        h.setDescription(blankToNull(form.getDescription()));
        h.setUseYn(HolidaySaveForm.yn(form.getUseYn()));
        h.setDeleteYn("N");

        try {
            mapper.insert(h);
        } catch (DuplicateKeyException dup) {
            throw new IllegalArgumentException(
                "이미 등록된 공휴일입니다: " + form.getHolidayDate() + " " + form.getHolidayName(), dup);
        }

        auditLogger.write(AuditEvent.of("HOLIDAY_CREATE", "tb_holiday")
            .withTarget(h.getHolidayId())
            .withAfter("{\"date\":" + JsonUtils.quote(String.valueOf(h.getHolidayDate()))
                + ",\"name\":" + JsonUtils.quote(h.getHolidayName())
                + ",\"type\":" + JsonUtils.quote(h.getHolidayType()) + "}")
            .withResult("SUCCESS"));

        log.info("===HOLIDAY_CREATE_OK id={} date={} name={}",
            h.getHolidayId(), h.getHolidayDate(), h.getHolidayName());
        return h.getHolidayId();
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public void update(HolidaySaveForm form) {
        Objects.requireNonNull(form, "form");
        if (form.getHolidayId() == null || form.getHolidayId().isBlank()) {
            throw new IllegalArgumentException("holidayId required");
        }
        Holiday existing = mapper.findById(form.getHolidayId());
        if (existing == null) {
            throw new IllegalArgumentException("공휴일을 찾을 수 없습니다.");
        }
        validateDup(form.getHolidayDate(), form.getHolidayName(), existing.getHolidayId());

        existing.setYear(form.resolveYear());
        existing.setHolidayDate(form.getHolidayDate());
        existing.setHolidayName(form.getHolidayName().trim());
        existing.setHolidayType(form.getHolidayType());
        existing.setDescription(blankToNull(form.getDescription()));
        existing.setUseYn(HolidaySaveForm.yn(form.getUseYn()));

        mapper.update(existing);

        auditLogger.write(AuditEvent.of("HOLIDAY_UPDATE", "tb_holiday")
            .withTarget(existing.getHolidayId())
            .withAfter("{\"name\":" + JsonUtils.quote(existing.getHolidayName())
                + ",\"useYn\":" + JsonUtils.quote(existing.getUseYn()) + "}")
            .withResult("SUCCESS"));
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public void toggleUse(String holidayId, boolean active) {
        Holiday existing = mapper.findById(holidayId);
        if (existing == null) {
            throw new IllegalArgumentException("공휴일을 찾을 수 없습니다.");
        }
        String next = active ? "Y" : "N";
        if (next.equals(existing.getUseYn())) return;
        mapper.updateUseYn(holidayId, next);

        auditLogger.write(AuditEvent.of("HOLIDAY_USE_TOGGLE", "tb_holiday")
            .withTarget(holidayId)
            .withBefore("{\"useYn\":" + JsonUtils.quote(existing.getUseYn()) + "}")
            .withAfter("{\"useYn\":" + JsonUtils.quote(next) + "}")
            .withResult("SUCCESS"));
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public void softDelete(String holidayId) {
        if (holidayId == null || holidayId.isBlank()) {
            throw new IllegalArgumentException("holidayId required");
        }
        Holiday existing = mapper.findById(holidayId);
        if (existing == null) {
            throw new IllegalArgumentException("이미 삭제되었거나 존재하지 않습니다.");
        }
        int affected = mapper.softDelete(holidayId);
        if (affected == 0) {
            throw new IllegalStateException("삭제 처리 실패: " + holidayId);
        }
        auditLogger.write(AuditEvent.of("HOLIDAY_DELETE", "tb_holiday")
            .withTarget(holidayId)
            .withBefore("{\"name\":" + JsonUtils.quote(existing.getHolidayName()) + "}")
            .withResult("SUCCESS"));
    }

    private void validateDup(LocalDate date, String name, String excludeId) {
        if (date == null) {
            throw new IllegalArgumentException("공휴일 날짜를 입력하세요.");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("공휴일 명칭을 입력하세요.");
        }
        if (mapper.existsByDate(date, name.trim(), excludeId) > 0) {
            throw new IllegalArgumentException(
                "같은 날짜·명칭의 공휴일이 이미 존재합니다.");
        }
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
