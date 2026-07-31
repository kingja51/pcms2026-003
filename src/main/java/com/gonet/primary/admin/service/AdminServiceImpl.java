package com.gonet.primary.admin.service;

import com.gonet.common.web.ResourceNotFoundException;
import com.gonet.common.audit.AuditEvent;
import com.gonet.common.audit.AuditLogger;
import com.gonet.common.crypto.AesGcmCipher;
import com.gonet.common.crypto.EmailHasher;
import com.gonet.common.util.JsonUtils;
import com.gonet.common.util.UuidV7Generator;
import com.gonet.common.util.WithdrawReasons;
import com.gonet.primary.admin.dto.Admin;
import com.gonet.primary.admin.dto.AdminGroupOption;
import com.gonet.primary.admin.dto.AdminPasswordForm;
import com.gonet.primary.admin.dto.AdminProfileForm;
import com.gonet.primary.admin.dto.AdminSaveForm;
import com.gonet.primary.admin.dto.AdminSearch;
import com.gonet.primary.admin.dto.RoleOption;
import com.gonet.primary.admin.mapper.AdminGroupMapper;
import com.gonet.primary.admin.mapper.AdminMapper;
import com.gonet.primary.admin.mapper.RoleMapper;
import com.gonet.primary.system.department.dto.Department;
import com.gonet.primary.system.department.mapper.DepartmentMapper;
import org.egovframe.rte.fdl.cmmn.EgovAbstractServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 관리자 서비스 구현 (eGov {@link EgovAbstractServiceImpl} 상속).
 *
 * <p>CUD 트랜잭션: Primary DataSource 고정, readOnly=false.
 * <p>PII 암호화: {@link com.gonet.config.interceptor.EncryptInterceptor} 가 자동 처리 —
 *    서비스는 평문 전달, DB 저장 시 {AG}base64 로 변환됨.
 * <p>email_hash: {@link EmailHasher} 가 소문자 정규화 후 HMAC-SHA256(hex) — 중복/검색용.
 * <p>role_ids CSV: tb_admin_role INSERT/UPDATE 후 SP {@code sp_rebuild_admin_role_csv} 호출 —
 *    closure table 로 조상→후손 전개한 결과를 denormalized 저장.
 */
@Service
@Transactional(readOnly = true, transactionManager =
    com.gonet.config.datasource.PrimaryDataSourceConfig.TRANSACTION_MGR)
public class AdminServiceImpl extends EgovAbstractServiceImpl implements AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminServiceImpl.class);

    private static final int PASSWORD_EXPIRE_DAYS   = 90;
    private static final int WITHDRAW_RETENTION_YRS = 3;
    /** 비밀번호 재사용 금지 — 최근 N건의 이력과 비교해 일치 시 거부. */
    private static final int PASSWORD_HISTORY_CHECK = 5;

    /**
     * tb_admin_withdraw.withdraw_reason 카테고리 집합 — 설계서 코멘트 'RESIGN|TRANSFER|FORCE|etc.' 기반.
     * DB 컬럼은 VARCHAR(50) (CHECK 제약 없음) 이지만 자유 텍스트 저장 시 감사 추적성 저하 + truncation 위험.
     * 자유 텍스트 원문은 감사 로그로만 분리 보관.
     */
    private static final Set<String> WITHDRAW_CATEGORIES =
        Set.of("RESIGN", "TRANSFER", "FORCE", "ADMIN_FORCE");

    private final AdminMapper       mapper;
    private final AdminGroupMapper  groupMapper;
    private final RoleMapper        roleMapper;
    private final DepartmentMapper  departmentMapper;
    private final PasswordEncoder   passwordEncoder;
    private final EmailHasher       emailHasher;
    private final AuditLogger       auditLogger;
    private final AesGcmCipher      cipher;

    public AdminServiceImpl(AdminMapper mapper,
                            AdminGroupMapper groupMapper,
                            RoleMapper roleMapper,
                            DepartmentMapper departmentMapper,
                            PasswordEncoder passwordEncoder,
                            EmailHasher emailHasher,
                            AuditLogger auditLogger,
                            AesGcmCipher cipher) {
        this.mapper = mapper;
        this.groupMapper = groupMapper;
        this.roleMapper = roleMapper;
        this.departmentMapper = departmentMapper;
        this.passwordEncoder = passwordEncoder;
        this.emailHasher = emailHasher;
        this.auditLogger = auditLogger;
        this.cipher = cipher;
    }

    /** departmentId → department_name 동기화. NULL/blank → 둘 다 NULL. */
    private void applyDepartment(Admin entity, String departmentId) {
        String id = blankToNull(departmentId);
        entity.setDepartmentId(id);
        if (id == null) {
            entity.setDepartmentName(null);
            return;
        }
        Department dept = departmentMapper.findById(id);
        if (dept == null) {
            throw new ResourceNotFoundException("존재하지 않는 부서입니다.");
        }
        entity.setDepartmentName(dept.getDepartmentName());
    }

    // ------------------------------------------------------------------
    // 조회
    // ------------------------------------------------------------------

    @Override
    public List<Admin> search(AdminSearch search) {
        AdminSearch s = normalize(search);
        return mapper.findList(s);
    }

    @Override
    public int count(AdminSearch search) {
        return mapper.countList(normalize(search));
    }

    @Override
    public Admin get(String adminId) {
        if (adminId == null || adminId.isBlank()) return null;
        return mapper.findById(adminId);
    }

    @Override
    public boolean existsLoginId(String loginId) {
        if (loginId == null || loginId.isBlank()) return false;
        return mapper.existsByLoginId(loginId.trim().toLowerCase(), null) > 0;
    }

    @Override
    public List<String> getDirectRoleIds(String adminId) {
        if (adminId == null || adminId.isBlank()) return List.of();
        return mapper.findRoleIdsByAdminId(adminId);
    }

    @Override
    public List<AdminGroupOption> listGroups() { return groupMapper.findActiveOptions(); }

    @Override
    public List<RoleOption> listRoles()       { return roleMapper.findActiveOptions(); }

    // ------------------------------------------------------------------
    // CUD
    // ------------------------------------------------------------------

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = com.gonet.config.datasource.PrimaryDataSourceConfig.TRANSACTION_MGR)
    public String create(AdminSaveForm form) {
        Objects.requireNonNull(form, "form");
        if (form.getPassword() == null || form.getPassword().isBlank()) {
            throw new IllegalArgumentException("비밀번호는 필수입니다.");
        }

        AdminGroupOption group = requireGroup(form.getAdminGroupId());
        String emailHash = emailHasher.hash(form.getEmail());

        validateDuplicates(form.getLoginId(), emailHash, null);

        Admin a = new Admin();
        a.setAdminId(UuidV7Generator.generate("ADM"));
        a.setAdminGroupId(group.getAdminGroupId());
        a.setLoginId(form.getLoginId().trim().toLowerCase());
        a.setPassword(passwordEncoder.encode(form.getPassword()));

        LocalDateTime now = LocalDateTime.now();
        a.setPasswordChangedAt(now);
        a.setPasswordExpireAt(now.plusDays(PASSWORD_EXPIRE_DAYS));

        a.setAdminName(form.getAdminName().trim());
        a.setEmail(form.getEmail().trim());
        a.setEmailHash(emailHash);
        a.setPhone(blankToNull(form.getPhone()));

        applyDepartment(a, form.getDepartmentId());

        a.setStatus(form.getStatus() == null ? "ACTIVE" : form.getStatus());
        a.setTwoFactorEnabledYn(form.getTwoFactorEnabledYn() == null ? "N" : form.getTwoFactorEnabledYn());
        a.setIpWhitelist(blankToNull(form.getIpWhitelist()));
        a.setRemarks(blankToNull(form.getRemarks()));
        a.setLoginFailCount(0);

        try {
            mapper.insert(a);
        } catch (DuplicateKeyException dup) {
            throw new IllegalArgumentException("이미 사용 중인 로그인 ID 또는 이메일입니다.", dup);
        }

        // 비밀번호 이력
        mapper.insertPasswordHistory(
            UuidV7Generator.generate("APH"), a.getAdminId(), a.getPassword(), now);

        // 역할 매핑 → role_ids CSV 재계산
        syncRoles(a.getAdminId(), form.getRoleIds());

        return a.getAdminId();
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = com.gonet.config.datasource.PrimaryDataSourceConfig.TRANSACTION_MGR)
    public void update(AdminSaveForm form) {
        Objects.requireNonNull(form, "form");
        if (form.getAdminId() == null || form.getAdminId().isBlank()) {
            throw new IllegalArgumentException("adminId required");
        }
        Admin existing = mapper.findById(form.getAdminId());
        if (existing == null) {
            throw new ResourceNotFoundException("관리자를 찾을 수 없습니다.");
        }

        AdminGroupOption group = requireGroup(form.getAdminGroupId());
        String emailHash = emailHasher.hash(form.getEmail());
        validateDuplicates(existing.getLoginId() /* login_id 는 수정 불가 */,
                            emailHash, form.getAdminId());

        existing.setAdminGroupId(group.getAdminGroupId());
        existing.setAdminName(form.getAdminName().trim());
        existing.setEmail(form.getEmail().trim());
        existing.setEmailHash(emailHash);
        existing.setPhone(blankToNull(form.getPhone()));
        applyDepartment(existing, form.getDepartmentId());
        existing.setStatus(form.getStatus());
        existing.setTwoFactorEnabledYn(form.getTwoFactorEnabledYn());
        existing.setIpWhitelist(blankToNull(form.getIpWhitelist()));
        existing.setRemarks(blankToNull(form.getRemarks()));

        mapper.update(existing);
        syncRoles(existing.getAdminId(), form.getRoleIds());
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = com.gonet.config.datasource.PrimaryDataSourceConfig.TRANSACTION_MGR)
    public void resetPassword(String adminId, String newPassword) {
        if (adminId == null || adminId.isBlank()) {
            throw new IllegalArgumentException("adminId required");
        }
        if (newPassword == null || newPassword.length() < 10) {
            throw new IllegalArgumentException("비밀번호는 10자 이상이어야 합니다.");
        }
        Admin existing = mapper.findById(adminId);
        if (existing == null) {
            throw new ResourceNotFoundException("관리자를 찾을 수 없습니다.");
        }
        String encoded = passwordEncoder.encode(newPassword);
        LocalDateTime now = LocalDateTime.now();
        mapper.updatePassword(adminId, encoded, now, now.plusDays(PASSWORD_EXPIRE_DAYS));
        mapper.insertPasswordHistory(UuidV7Generator.generate("APH"), adminId, encoded, now);
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = com.gonet.config.datasource.PrimaryDataSourceConfig.TRANSACTION_MGR)
    public void unlock(String adminId) {
        if (adminId == null || adminId.isBlank()) {
            throw new IllegalArgumentException("adminId required");
        }
        int n = mapper.unlockAdmin(adminId);
        if (n == 0) {
            throw new ResourceNotFoundException("관리자를 찾을 수 없습니다.");
        }
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = com.gonet.config.datasource.PrimaryDataSourceConfig.TRANSACTION_MGR)
    public void enableTwoFactor(String adminId, String secret) {
        if (adminId == null || adminId.isBlank()) {
            throw new IllegalArgumentException("adminId required");
        }
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("secret required");
        }
        // updateTwoFactor 는 secret 을 bare @Param 으로 전달 → MyBatis ParamMap 으로 래핑되어
        // EncryptInterceptor(@Encrypt 필드 기반) 가 타지 않는다. 여기서 명시적으로 암호화해
        // two_factor_secret 이 평문으로 저장되는 것을 막는다. (읽기는 Admin DTO 의 @Encrypt 로 자동 복호화)
        String encryptedSecret = cipher.encrypt(secret);
        mapper.updateTwoFactor(adminId, encryptedSecret, "Y");
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = com.gonet.config.datasource.PrimaryDataSourceConfig.TRANSACTION_MGR)
    public void softDelete(String adminId, String reason) {
        Admin existing = mapper.findById(adminId);
        if (existing == null) {
            throw new ResourceNotFoundException("이미 삭제되었거나 존재하지 않는 관리자입니다.");
        }

        // 호출 경로별 기본값:
        //   - MyPageAdminMngController(본인 탈퇴): 자유 텍스트 사유 → RESIGN + 원문 note
        //   - AdminMngController(강제 탈퇴): "FORCE" 코드 전달 → 그대로 저장
        String defaultCategory = com.gonet.common.audit.AuditContext.userId() != null
            && com.gonet.common.audit.AuditContext.userId().equals(adminId) ? "RESIGN" : "FORCE";
        WithdrawReasons.Normalized n = WithdrawReasons.normalize(
            reason, WITHDRAW_CATEGORIES, defaultCategory);

        mapper.insertWithdraw(existing,
            n.category(),
            com.gonet.common.audit.AuditContext.userId(),
            LocalDateTime.now().plusYears(WITHDRAW_RETENTION_YRS));
        mapper.clearRoles(adminId);
        int affected = mapper.softDelete(adminId);
        if (affected == 0) {
            throw new IllegalArgumentException("이미 삭제된 관리자입니다.");
        }

        writeWithdrawAudit("ADMIN_WITHDRAW", "tb_admin_withdraw",
            adminId, "ADMIN", existing.getLoginId(), n);
    }

    /**
     * 감사 로그 공통 기록 — 탈퇴 이벤트. category + 자유 텍스트 note 를 JSON 으로.
     * DB 컬럼은 카테고리 enum 만 저장하고, 원문은 {@code log_audit.after_value} 에 보관한다.
     */
    private void writeWithdrawAudit(String action, String entity,
                                     String actorId, String actorType, String loginId,
                                     WithdrawReasons.Normalized normalized) {
        StringBuilder after = new StringBuilder(128)
            .append("{\"category\":").append(JsonUtils.quote(normalized.category()));
        if (normalized.note() != null) {
            after.append(",\"note\":").append(JsonUtils.quote(normalized.note()));
        }
        after.append("}");
        auditLogger.write(AuditEvent.of(action, entity)
            .withActor(actorId, actorType, loginId)
            .withTarget(actorId)
            .withResult("SUCCESS")
            .withAfter(after.toString()));
        log.info("==={} ok actorId={} category={} noteLen={}",
            action, actorId, normalized.category(),
            normalized.note() == null ? 0 : normalized.note().length());
    }

    // ------------------------------------------------------------------
    // 마이페이지 (본인 self-service)
    // ------------------------------------------------------------------

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = com.gonet.config.datasource.PrimaryDataSourceConfig.TRANSACTION_MGR)
    public void updateSelfProfile(String adminId, AdminProfileForm form) {
        Objects.requireNonNull(form, "form");
        Admin existing = mapper.findById(adminId);
        if (existing == null) throw new ResourceNotFoundException("관리자를 찾을 수 없습니다.");

        String emailHash = emailHasher.hash(form.getEmail());
        if (mapper.existsByEmailHash(emailHash, adminId) > 0) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }

        existing.setAdminName(form.getAdminName().trim());
        existing.setEmail(form.getEmail().trim());
        existing.setEmailHash(emailHash);
        existing.setPhone(blankToNull(form.getPhone()));
        applyDepartment(existing, form.getDepartmentId());

        mapper.updateSelfProfile(existing);
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = com.gonet.config.datasource.PrimaryDataSourceConfig.TRANSACTION_MGR)
    public void changePassword(String adminId, AdminPasswordForm form) {
        Objects.requireNonNull(form, "form");
        Admin existing = mapper.findById(adminId);
        if (existing == null) throw new ResourceNotFoundException("관리자를 찾을 수 없습니다.");

        if (!passwordEncoder.matches(form.getCurrentPassword(), existing.getPassword())) {
            throw new IllegalArgumentException("현재 비밀번호가 일치하지 않습니다.");
        }
        // 재사용 금지 — 현재 비밀번호 + 최근 PASSWORD_HISTORY_CHECK 건 이력과 BCrypt matches 비교.
        if (passwordEncoder.matches(form.getNewPassword(), existing.getPassword())) {
            throw new IllegalArgumentException("새 비밀번호는 현재 비밀번호와 달라야 합니다.");
        }
        for (String oldHash : mapper.findRecentPasswordHashes(adminId, PASSWORD_HISTORY_CHECK)) {
            if (oldHash == null || oldHash.isBlank()) continue;
            if (passwordEncoder.matches(form.getNewPassword(), oldHash)) {
                throw new IllegalArgumentException(
                    "최근 " + PASSWORD_HISTORY_CHECK + "회 사용한 비밀번호는 재사용할 수 없습니다.");
            }
        }

        String encoded = passwordEncoder.encode(form.getNewPassword());
        LocalDateTime now = LocalDateTime.now();
        mapper.updatePassword(adminId, encoded, now, now.plusDays(PASSWORD_EXPIRE_DAYS));
        mapper.insertPasswordHistory(UuidV7Generator.generate("APH"), adminId, encoded, now);
    }

    // ------------------------------------------------------------------
    // 내부 헬퍼
    // ------------------------------------------------------------------

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private void syncRoles(String adminId, List<String> requestedRoleIds) {
        mapper.clearRoles(adminId);
        if (requestedRoleIds != null) {
            for (String roleId : requestedRoleIds) {
                if (roleId == null || roleId.isBlank()) continue;
                mapper.insertRole(UuidV7Generator.generate("ARL"), adminId, roleId);
            }
        }
        mapper.callRebuildRoleCsv(adminId);
        // role_ids CSV 재계산 후 role_codes CSV 도 동기화 (로그인 시 hasRole 체크용)
        mapper.updateRoleCodes(adminId);
    }

    private void validateDuplicates(String loginId, String emailHash, String excludeAdminId) {
        if (loginId != null && !loginId.isBlank()) {
            if (mapper.existsByLoginId(loginId.trim().toLowerCase(), excludeAdminId) > 0) {
                throw new IllegalArgumentException("이미 사용 중인 로그인 ID 입니다: " + loginId);
            }
        }
        if (emailHash != null && mapper.existsByEmailHash(emailHash, excludeAdminId) > 0) {
            throw new IllegalArgumentException("이미 등록된 이메일입니다.");
        }
    }

    private AdminGroupOption requireGroup(String adminGroupId) {
        if (adminGroupId == null || adminGroupId.isBlank()) {
            throw new IllegalArgumentException("관리자 그룹을 선택하세요.");
        }
        AdminGroupOption g = groupMapper.findById(adminGroupId);
        if (g == null) {
            throw new IllegalArgumentException("유효하지 않은 관리자 그룹입니다.");
        }
        return g;
    }

    private static AdminSearch normalize(AdminSearch s) {
        return s == null ? new AdminSearch() : s;
    }
}
