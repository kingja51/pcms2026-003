package com.gonet.primary.system.stat.service;

import com.gonet.config.datasource.LoggingDataSourceConfig;
import com.gonet.logging.access.dto.AccessStatDaily;
import com.gonet.logging.access.dto.AccessStatUriDaily;
import com.gonet.logging.access.dto.DailyVisitStat;
import com.gonet.logging.access.mapper.AccessStatMapper;
import org.egovframe.rte.fdl.cmmn.EgovAbstractServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true, transactionManager = LoggingDataSourceConfig.TRANSACTION_MGR)
public class AccessStatViewServiceImpl extends EgovAbstractServiceImpl
        implements AccessStatViewService {

    private final AccessStatMapper mapper;

    public AccessStatViewServiceImpl(AccessStatMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Map<String, Object> summary(LocalDate from, LocalDate to, String siteId) {
        return mapper.periodSummary(from, to, blank(siteId));
    }

    @Override
    public List<AccessStatDaily> dailySeries(LocalDate from, LocalDate to, String siteId) {
        return mapper.dailySeries(from, to, blank(siteId));
    }

    @Override
    public List<AccessStatDaily> siteShare(LocalDate from, LocalDate to) {
        return mapper.siteShareSummary(from, to);
    }

    @Override
    public List<AccessStatDaily> userTypeShare(LocalDate from, LocalDate to, String siteId) {
        return mapper.userTypeShareSummary(from, to, blank(siteId));
    }

    @Override
    public List<AccessStatDaily> hourlyPattern(LocalDate from, LocalDate to, String siteId) {
        return mapper.hourlySummary(from, to, blank(siteId));
    }

    @Override
    public List<AccessStatUriDaily> topPages(LocalDate from, LocalDate to, String siteId, int limit) {
        int n = (limit <= 0 || limit > 100) ? 20 : limit;
        return mapper.topPages(from, to, blank(siteId), n);
    }

    @Override
    public List<DailyVisitStat> dailyVisit(LocalDate from, LocalDate to, String siteId) {
        return mapper.listDailyVisit(from, to, blank(siteId));
    }

    @Override
    public List<DailyVisitStat> dailyVisitBySite(LocalDate from, LocalDate to) {
        return mapper.sumDailyVisitBySite(from, to);
    }

    private static String blank(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
