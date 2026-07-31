package com.gonet.primary.system.access.service;

import com.gonet.config.cache.CacheConfig;
import com.gonet.primary.system.access.dto.RoleUrlAccessRule;
import com.gonet.primary.system.access.mapper.RoleUrlAccessMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * tb_role_url_access 규칙 캐시 서비스.
 *
 * <p>규칙 전체를 Caffeine 에 적재. 관리자 편집 후 {@link #evict()} 호출로 전체 무효화.
 * 단일 규칙 변경 빈도는 낮으므로 부분 무효화 대신 전체 리로드가 단순·안전.
 */
@Service
public class RoleUrlAccessService {

    private static final Logger log = LoggerFactory.getLogger(RoleUrlAccessService.class);
    private static final String ALL_KEY = "__ALL__";

    private final RoleUrlAccessMapper mapper;

    public RoleUrlAccessService(RoleUrlAccessMapper mapper) {
        this.mapper = mapper;
    }

    @Cacheable(value = CacheConfig.ROLE_URL_ACCESS, key = "'" + ALL_KEY + "'")
    public List<RoleUrlAccessRule> getAllActive() {
        List<RoleUrlAccessRule> rules = mapper.findAllActive();
        log.info("===RoleUrlAccess rules loaded: {} rows", rules.size());
        return rules;
    }

    @CacheEvict(value = CacheConfig.ROLE_URL_ACCESS, allEntries = true)
    public void evict() {
        log.info("===RoleUrlAccess cache evicted");
    }
}
