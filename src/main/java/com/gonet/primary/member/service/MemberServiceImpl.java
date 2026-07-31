package com.gonet.primary.member.service;

import com.gonet.common.audit.AuditContext;
import com.gonet.common.audit.AuditEvent;
import com.gonet.common.audit.AuditLogger;
import com.gonet.common.crypto.EmailHasher;
import com.gonet.common.mail.MailService;
import com.gonet.common.util.JsonUtils;
import com.gonet.common.util.RandomPasswordGenerator;
import com.gonet.common.util.UuidV7Generator;
import com.gonet.common.util.WithdrawReasons;
import com.gonet.primary.member.dto.*;
import com.gonet.primary.member.mapper.MemberConsentMapper;
import com.gonet.primary.member.mapper.MemberMapper;
import com.gonet.primary.system.mail.dto.MailTemplate;
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
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 회원 서비스 구현 — 가입·프로필·비밀번호·탈퇴 + 관리자 조회.
 *
 * <p>eGov 표준 {@link EgovAbstractServiceImpl} 상속.
 * <p>PII 필드는 {@code EncryptInterceptor} 가 자동 {AG} AES-GCM 암복호화. email/phone 은
 * 중복확인용 HMAC-SHA256 hash 를 서비스에서 직접 세팅.
 *
 * <p>이메일 인증은 본 1차 구현에서 생략 — status=ACTIVE 로 바로 활성.
 * 메일 발송·토큰 검증은 S5 후속 작업.
 */

@Service
@Transactional(readOnly = true, transactionManager =
    com.gonet.config.datasource.PrimaryDataSourceConfig.TRANSACTION_MGR)
public class MemberServiceImpl extends EgovAbstractServiceImpl implements MemberService {

    private static final Logger log = LoggerFactory.getLogger(MemberServiceImpl.class);

    private static final int PASSWORD_EXPIRE_DAYS   = 180;
    private static final int WITHDRAW_RETENTION_YRS = 5;       // 전자상거래법 최대 5년
    /** 비밀번호 재사용 금지 — 최근 N건의 이력과 비교해 일치 시 거부. */
    private static final int PASSWORD_HISTORY_CHECK = 5;

    private static final String CONSENT_VERSION     = "1.0";    // 약관 버전 — 정책 변경 시 bump

    /**
     * tb_member_withdraw.withdraw_reason CHECK 허용 enum.
     * 스키마 정의: {@code CHECK (withdraw_reason IN ('USER_REQUEST','ADMIN_FORCE','DORMANT_EXPIRED'))}.
     * 서비스 계층에서 DB 저장 전에 정규화 — 사용자 입력 자유 텍스트는 감사 로그로만 보냄 (PII 최소화).
     */
    private static final Set<String> WITHDRAW_CATEGORIES =
        Set.of("USER_REQUEST", "ADMIN_FORCE", "DORMANT_EXPIRED");

    private final MemberMapper        mapper;
    private final MemberConsentMapper consentMapper;
    private final PasswordEncoder     passwordEncoder;
    private final EmailHasher         emailHasher;
    private final MailService         mailService;
    private final AuditLogger         auditLogger;

    public MemberServiceImpl(MemberMapper mapper,
                             MemberConsentMapper consentMapper,
                             PasswordEncoder passwordEncoder,
                             EmailHasher emailHasher,
                             MailService mailService,
                             AuditLogger auditLogger) {
        this.mapper = mapper;
        this.consentMapper = consentMapper;
        this.passwordEncoder = passwordEncoder;
        this.emailHasher = emailHasher;
        this.mailService = mailService;
        this.auditLogger = auditLogger;
    }

    // ------------------------------------------------------------------
    // 조회
    // ------------------------------------------------------------------

    @Override public List<Member> search(MemberSearch s) { return mapper.findList(normalize(s)); }
    @Override public int          count(MemberSearch s) { return mapper.countList(normalize(s)); }

    @Override
    public Member get(String memberId) {
        if (memberId == null || memberId.isBlank()) return null;
        return mapper.findById(memberId);
    }

    @Override
    public List<MemberConsent> listConsents(String memberId) {
        if (memberId == null || memberId.isBlank()) return List.of();
        return consentMapper.findByMemberId(memberId);
    }

    // ------------------------------------------------------------------
    // 회원 가입
    // ------------------------------------------------------------------

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = com.gonet.config.datasource.PrimaryDataSourceConfig.TRANSACTION_MGR)
    public String join(MemberJoinForm form, JoinSessionData joinSess,
                        String siteId, String clientIp, String userAgent) {
        Objects.requireNonNull(form, "form");
        Objects.requireNonNull(joinSess, "joinSess");

        log.info("===MEMBER_SERVICE_JOIN_ENTRY siteId={} loginId={} userType={} diVerified={} verifiedDiLen={} verifiedDiHashLen={} verifiedName={} verifiedPhone={} clientIp={}",
                siteId,
                form.getLoginId(),
                joinSess.getUserType(),
                joinSess.isDiVerified(),
                joinSess.getVerifiedDi() == null ? 0 : joinSess.getVerifiedDi().length(),
                joinSess.getVerifiedDiHash() == null ? 0 : joinSess.getVerifiedDiHash().length(),
                joinSess.getVerifiedName(),
                joinSess.getVerifiedPhone(),
                clientIp);

        if (siteId == null || siteId.isBlank()) {
            throw new IllegalArgumentException("siteId required");
        }
        if (!joinSess.isDiVerified() || joinSess.getVerifiedDi() == null) {
            log.warn("MEMBER_JOIN_DI_MISSING diVerified={} verifiedDi={} — JoinSessionData 가 절차를 거치지 않은 채 join() 진입",
                    joinSess.isDiVerified(),
                    joinSess.getVerifiedDi() == null ? "null" : "len=" + joinSess.getVerifiedDi().length());
            throw new IllegalArgumentException("본인인증이 완료되지 않았습니다. 처음부터 다시 시도해 주세요.");
        }

        String loginId   = form.getLoginId().trim().toLowerCase();
        String emailHash = emailHasher.hash(form.getEmail());

        if (mapper.existsByLoginId(siteId, loginId, null) > 0) {
            throw new IllegalArgumentException("이미 사용 중인 로그인 ID 입니다: " + loginId);
        }
        if (mapper.existsByEmailHash(emailHash, null) > 0) {
            throw new IllegalArgumentException("이미 가입된 이메일입니다.");
        }

        boolean child = "CHILD".equalsIgnoreCase(form.getUserType());
        // CHILD: 자녀의 di 도 부모 DI 를 공유하도록 정책 결정 (parent_di 와 동일 값).
        // ADULT: 본인 DI = 세션 DI.
        String diPlain    = joinSess.getVerifiedDi();
        String diHash     = joinSess.getVerifiedDiHash();
        if (diHash == null || diHash.isBlank()) {
            // 세션에 해시가 없으면 지금 계산 (안전장치)
            diHash = emailHasher.hash(diPlain);
        }

        Member m = new Member();
        String memberId = UuidV7Generator.generate("MBR");
        m.setMemberId(memberId);
        // 가입 시점에는 SecurityContext 가 비어 AuditInterceptor 가 ANONYMOUS 를 주입한다.
        // 회원 본인의 member_id 를 created_by/updated_by 로 명시 세팅 — 인터셉터의 null 가드를 통과한다.
        m.setCreatedBy(memberId);
        m.setCreatedIp(clientIp);
        m.setUpdatedBy(memberId);
        m.setUpdatedIp(clientIp);
        m.setSiteId(siteId);
        m.setLoginId(loginId);
        m.setPassword(passwordEncoder.encode(form.getPassword()));
        LocalDateTime now = LocalDateTime.now();
        m.setPasswordChangedAt(now);
        m.setPasswordExpireAt(now.plusDays(PASSWORD_EXPIRE_DAYS));
        m.setMemberName(form.getMemberName().trim());
        m.setNickname(blankToNull(form.getNickname()));
        m.setEmail(form.getEmail().trim());
        m.setEmailHash(emailHash);
        m.setEmailVerifiedYn("N");
        m.setPhone(blankToNull(form.getPhone()));
        if (m.getPhone() != null) {
            m.setPhoneHash(emailHasher.hash(m.getPhone()));  // HMAC 동일 키 재사용
        }
        m.setBirthDate(blankToNull(form.getBirthDate()));
        m.setBirthYear(extractBirthYear(form.getBirthDate()));   // 평문 — 연령대 통계용 (PIPA 일반 개인정보)
        m.setGender(blankToNull(form.getGender()));

        // DI — 본인(성인) 또는 부모(자녀)로 동일 값
        m.setDi(diPlain);
        m.setDiHash(diHash);

        if (child) {
            // 법정대리인 정보: parent_name 은 form.parentName, parent_di 는 세션 DI
            String parentName = blankToNull(form.getParentName() != null
                ? form.getParentName() : joinSess.getVerifiedName());
            m.setParentName(parentName);
            m.setParentDi(diPlain);
            m.setParentDiHash(diHash);
        } else {
            m.setParentName(null);
            m.setParentDi(null);
            m.setParentDiHash(null);
        }

        m.setJoinType(resolveJoinType(joinSess, userAgent));
        m.setStatus("ACTIVE");                                   // TODO: EMAIL_PENDING + 메일인증
        m.setPrivacyAgreeYn(form.getPrivacyAgreeYn());
        m.setTermsAgreeYn(form.getTermsAgreeYn());
        m.setMarketingAgreeYn(form.getMarketingAgreeYn());
        m.setSmsAgreeYn(form.getSmsAgreeYn());
        m.setEmailAgreeYn(form.getEmailAgreeYn());

        try {
            mapper.insert(m);
        } catch (DuplicateKeyException dup) {
            // UNIQUE (site_id, member_name, di_hash, parent_di_hash) 또는 login_id / email_hash 위반
            throw new IllegalArgumentException(
                "이미 가입된 정보입니다. 동일 이름·본인확인 조합이 이미 존재합니다.", dup);
        }

        // 비밀번호 이력
        mapper.insertPasswordHistory(UuidV7Generator.generate("MPH"), m.getMemberId(), m.getPassword(), now);

        // 동의 이력 5종 기록 (필수 + 선택 전부 — 현재 선택을 그대로 스냅샷)
        recordConsent(m.getMemberId(), MemberConsent.TYPE_TERMS,     form.getTermsAgreeYn(),     clientIp, userAgent, now);
        recordConsent(m.getMemberId(), MemberConsent.TYPE_PRIVACY,   form.getPrivacyAgreeYn(),   clientIp, userAgent, now);
        recordConsent(m.getMemberId(), MemberConsent.TYPE_MARKETING, form.getMarketingAgreeYn(), clientIp, userAgent, now);
        recordConsent(m.getMemberId(), MemberConsent.TYPE_SMS,       form.getSmsAgreeYn(),       clientIp, userAgent, now);
        recordConsent(m.getMemberId(), MemberConsent.TYPE_EMAIL,     form.getEmailAgreeYn(),     clientIp, userAgent, now);

        // 축하 메일 발송 — 실패해도 가입은 유지 (사용자 경험 우선). 로그로 실패 추적.
        sendTemplate(MailTemplate.CODE_MEMBER_WELCOME, m.getEmail(), Map.of(
            "memberName", m.getMemberName(),
            "loginId",    m.getLoginId(),
            "siteName",   "",                        // SiteContext 미주입 — 템플릿이 fallback 처리
            "sentAt",     now,
            "loginUrl",   "/member/login"
        ));

        return m.getMemberId();
    }

    // ------------------------------------------------------------------
    // 아이디 중복확인 (비로그인)
    // ------------------------------------------------------------------

    @Override
    public boolean existsLoginId(String siteId, String loginId) {
        if (siteId == null || siteId.isBlank())   return false;
        if (loginId == null || loginId.isBlank()) return false;
        return mapper.existsByLoginId(siteId, loginId.trim().toLowerCase(), null) > 0;
    }

    // ------------------------------------------------------------------
    // 아이디·비밀번호 찾기 (비로그인)
    // ------------------------------------------------------------------

    @Override
    public Member findByEmail(String siteId, String email) {
        // siteId null 허용 — 회원은 단일 사이트만 가입 가정. find-id 컨트롤러에서
        // siteContext 미확정 시 null 로 호출. mapper SQL 이 siteId=null 분기 지원.
        if (email == null || email.isBlank())  return null;
        String emailHash = emailHasher.hash(email.trim());
        Member m = mapper.findByEmailHash(siteId, emailHash);
        log.info("===FIND_BY_EMAIL siteId={} hashPrefix={} found={}",
            siteId,
            emailHash == null ? "null" : emailHash.substring(0, Math.min(16, emailHash.length())),
            m == null ? "miss" : ("hit:" + m.getMemberId()));
        return m;
    }

    @Override
    public Member findIdByNameAndEmail(String siteId, String name, String email) {
        // 이름 + 이메일 AND 매칭. enumeration 위험 줄임 — 한 항목만 맞으면 null.
        if (email == null || email.isBlank()) return null;
        if (name  == null || name.isBlank())  return null;
        String emailHash = emailHasher.hash(email.trim());
        String normName  = name.trim();
        Member m = mapper.findByMemberNameAndEmailHash(siteId, normName, emailHash);
        log.info("===FIND_ID_BY_NAME_EMAIL siteId={} nameLen={} hashPrefix={} found={}",
            siteId, normName.length(),
            emailHash == null ? "null" : emailHash.substring(0, Math.min(16, emailHash.length())),
            m == null ? "miss" : ("hit:" + m.getMemberId()));
        return m;
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = com.gonet.config.datasource.PrimaryDataSourceConfig.TRANSACTION_MGR)
    public boolean selfResetPassword(String siteId, String loginId, String email) {
        // siteId null 허용 — findByEmail 와 동일 정책. mapper SQL 이 분기 지원.
        if (loginId == null || loginId.isBlank()) return false;
        if (email == null || email.isBlank())     return false;

        String emailHash = emailHasher.hash(email.trim());
        Member member = mapper.findByLoginIdAndEmailHash(
            siteId, loginId.trim().toLowerCase(), emailHash);
        log.info("===SELF_RESET_PWD lookup siteId={} loginId={} hashPrefix={} found={}",
            siteId, loginId,
            emailHash == null ? "null" : emailHash.substring(0, Math.min(16, emailHash.length())),
            member == null ? "miss" : ("hit:" + member.getMemberId() + " status=" + member.getStatus()));
        if (member == null) return false;
        if (!"ACTIVE".equals(member.getStatus())) return false;

        // 비로그인 본인 작업 — SecurityContext 비어있어 AuditInterceptor 가 ANONYMOUS 를 박을 위험.
        // 본인 확인(loginId + email) 통과한 직후이므로 회원 본인의 member_id 를 actor 로 강제.
        try {
            AuditContext.forceUserId(member.getMemberId());
            resetPasswordAndSendMail(member.getMemberId());
        } finally {
            AuditContext.clearForcedUserId();
        }
        return true;
    }

    // ------------------------------------------------------------------
    // 마이페이지 (본인)
    // ------------------------------------------------------------------

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = com.gonet.config.datasource.PrimaryDataSourceConfig.TRANSACTION_MGR)
    public void updateProfile(String memberId, MemberProfileForm form) {
        Objects.requireNonNull(form, "form");
        Member existing = mapper.findById(memberId);
        if (existing == null) throw new IllegalArgumentException("회원을 찾을 수 없습니다.");

        String emailHash = emailHasher.hash(form.getEmail());
        if (mapper.existsByEmailHash(emailHash, memberId) > 0) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }

        existing.setMemberName(form.getMemberName().trim());
        existing.setNickname(blankToNull(form.getNickname()));
        existing.setEmail(form.getEmail().trim());
        existing.setEmailHash(emailHash);
        existing.setPhone(blankToNull(form.getPhone()));
        existing.setPhoneHash(existing.getPhone() == null ? null : emailHasher.hash(existing.getPhone()));
        existing.setAddressZipcode(blankToNull(form.getAddressZipcode()));
        existing.setAddress(blankToNull(form.getAddress()));
        existing.setAddressDetail(blankToNull(form.getAddressDetail()));
        existing.setMarketingAgreeYn(form.getMarketingAgreeYn());
        existing.setSmsAgreeYn(form.getSmsAgreeYn());
        existing.setEmailAgreeYn(form.getEmailAgreeYn());

        mapper.updateProfile(existing);

        // 선택동의 변경 시 이력 재기록 (AuditContext IP 사용)
        LocalDateTime now = LocalDateTime.now();
        String ip = AuditContext.ip();
        recordConsent(memberId, MemberConsent.TYPE_MARKETING, form.getMarketingAgreeYn(), ip, null, now);
        recordConsent(memberId, MemberConsent.TYPE_SMS,       form.getSmsAgreeYn(),       ip, null, now);
        recordConsent(memberId, MemberConsent.TYPE_EMAIL,     form.getEmailAgreeYn(),     ip, null, now);
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = com.gonet.config.datasource.PrimaryDataSourceConfig.TRANSACTION_MGR)
    public void changePassword(String memberId, MemberPasswordForm form) {
        Objects.requireNonNull(form, "form");
        Member existing = mapper.findById(memberId);
        if (existing == null) throw new IllegalArgumentException("회원을 찾을 수 없습니다.");

        if (!passwordEncoder.matches(form.getCurrentPassword(), existing.getPassword())) {
            throw new IllegalArgumentException("현재 비밀번호가 일치하지 않습니다.");
        }
        // 재사용 금지 — 현재 비밀번호 + 최근 PASSWORD_HISTORY_CHECK 건의 이력과 비교.
        // BCrypt 는 같은 평문도 다른 해시가 나오므로 matches() 로 각 해시를 일일이 검증.
        if (passwordEncoder.matches(form.getNewPassword(), existing.getPassword())) {
            throw new IllegalArgumentException("새 비밀번호는 현재 비밀번호와 달라야 합니다.");
        }
        for (String oldHash : mapper.findRecentPasswordHashes(memberId, PASSWORD_HISTORY_CHECK)) {
            if (oldHash == null || oldHash.isBlank()) continue;
            if (passwordEncoder.matches(form.getNewPassword(), oldHash)) {
                throw new IllegalArgumentException(
                    "최근 " + PASSWORD_HISTORY_CHECK + "회 사용한 비밀번호는 재사용할 수 없습니다.");
            }
        }

        String encoded = passwordEncoder.encode(form.getNewPassword());
        LocalDateTime now = LocalDateTime.now();
        mapper.updatePassword(memberId, encoded, now, now.plusDays(PASSWORD_EXPIRE_DAYS));
        mapper.insertPasswordHistory(UuidV7Generator.generate("MPH"), memberId, encoded, now);

        // 보안 알림 메일 — 본인이 아닌 변경 시도 탐지 목적. 실패해도 변경은 유지.
        Map<String, Object> model = new java.util.HashMap<>();
        model.put("memberName", existing.getMemberName());
        model.put("loginId",    existing.getLoginId());
        model.put("changedAt",  now);
        model.put("clientIp",   AuditContext.ip());
        model.put("userAgent",  "");
        sendTemplate(MailTemplate.CODE_PASSWORD_CHANGED, existing.getEmail(), model);
    }

    @Override
    public boolean verifyReauth(String memberId, String memberName, String email, String password) {
        // 타이밍 공격 완화 — 모든 경로에서 BCrypt compare 를 반드시 1회 수행.
        // user-not-found 더미 해시 (BCryptPasswordEncoder 의 mitigateAgainstTimingAttack 과 유사 전략).
        final String dummyHash = "$2a$12$0000000000000000000000.000000000000000000000000000000";

        Member m = (memberId == null || memberId.isBlank()) ? null : mapper.findById(memberId);
        String storedHash      = (m != null) ? m.getPassword() : dummyHash;
        String inputName       = memberName == null ? "" : memberName.trim();
        String storedName      = (m != null && m.getMemberName() != null) ? m.getMemberName() : "";
        String inputEmail      = email == null ? "" : email.trim();
        String inputEmailHash  = inputEmail.isEmpty() ? "" : emailHasher.hash(inputEmail);
        String storedEmailHash = (m != null && m.getEmailHash() != null) ? m.getEmailHash() : "";

        boolean nameMatch  = !inputName.isEmpty() && inputName.equals(storedName);
        boolean emailMatch = !inputEmailHash.isEmpty() && inputEmailHash.equalsIgnoreCase(storedEmailHash);
        boolean pwMatch    = password != null && passwordEncoder.matches(password, storedHash);

        // 이름/이메일이 다르더라도 BCrypt 는 소비 — 응답 시간 균일화.
        return m != null && nameMatch && emailMatch && pwMatch;
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = com.gonet.config.datasource.PrimaryDataSourceConfig.TRANSACTION_MGR)
    public void withdraw(String memberId, String reason) {
        Member existing = mapper.findById(memberId);
        if (existing == null) throw new IllegalArgumentException("이미 탈퇴되었거나 존재하지 않는 회원입니다.");

        // 본인 셀프 탈퇴 경로 — status=USER_REQUEST 고정, reason 은 사용자 자유 텍스트 (선택)
        WithdrawReasons.Normalized n = WithdrawReasons.normalize(
            reason, WITHDRAW_CATEGORIES, "USER_REQUEST");

        LocalDateTime retentionExpireAt = LocalDateTime.now().plusYears(WITHDRAW_RETENTION_YRS);
        // withdraw_status = category 코드, withdraw_reason = 자유 텍스트 (note 가 null 이면 NULL 저장)
        mapper.insertWithdraw(existing, n.category(), n.note(), retentionExpireAt);
        int affected = mapper.softDelete(memberId);
        if (affected == 0) throw new IllegalArgumentException("이미 탈퇴된 회원입니다.");

        writeWithdrawAudit("MEMBER_WITHDRAW", "tb_member_withdraw",
            memberId, "MEMBER", existing.getLoginId(), n);

        // 탈퇴 완료 메일 (PII 는 이 시점에서는 복호화된 평문으로 접근 가능)
        Map<String, Object> model = new java.util.HashMap<>();
        model.put("memberName",        existing.getMemberName());
        model.put("loginId",           existing.getLoginId());
        model.put("siteName",          "");
        model.put("withdrawAt",        LocalDateTime.now());
        model.put("reason",            n.category());
        model.put("retentionExpireAt", retentionExpireAt);
        model.put("rejoinUrl",         "/member/join");
        sendTemplate(MailTemplate.CODE_MEMBER_WITHDRAW, existing.getEmail(), model);
    }

    /**
     * 감사 로그 공통 기록자 — 탈퇴/퇴사 이벤트. 카테고리와 자유 텍스트 note 를 JSON 으로 저장.
     * 본 프로젝트의 PII 최소화 정책상 자유 텍스트는 DB 가 아닌 {@code log_audit.after_value} 에만 보관.
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
    // 관리자
    // ------------------------------------------------------------------

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = com.gonet.config.datasource.PrimaryDataSourceConfig.TRANSACTION_MGR)
    public void adminUnlock(String memberId) {
        if (memberId == null || memberId.isBlank()) {
            throw new IllegalArgumentException("memberId required");
        }
        int n = mapper.unlockMember(memberId);
        if (n == 0) {
            throw new IllegalArgumentException("회원을 찾을 수 없습니다.");
        }
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = com.gonet.config.datasource.PrimaryDataSourceConfig.TRANSACTION_MGR)
    public void adminUpdateStatus(String memberId, String status) {
        if (!List.of("ACTIVE", "LOCKED", "EMAIL_PENDING", "SUSPENDED").contains(status)) {
            throw new IllegalArgumentException("허용되지 않은 상태값: " + status);
        }
        mapper.updateStatus(memberId, status);
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = com.gonet.config.datasource.PrimaryDataSourceConfig.TRANSACTION_MGR)
    public void resetPasswordAndSendMail(String memberId) {
        Member member = mapper.findById(memberId);
        if (member == null) throw new IllegalArgumentException("회원을 찾을 수 없습니다.");
        String email = member.getEmail();
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("회원 이메일이 등록되어 있지 않아 발송할 수 없습니다.");
        }

        // 1) 임시 비밀번호 생성 — 로컬 변수로만 보관, 로그·예외 메시지에 절대 기록 금지
        String tempPassword = RandomPasswordGenerator.generate();
        String encoded      = passwordEncoder.encode(tempPassword);
        LocalDateTime now   = LocalDateTime.now();

        // 2) DB 업데이트 — 임시 비밀번호는 즉시 만료 처리(다음 로그인에서 변경 강제)
        mapper.updatePassword(memberId, encoded, now, now);
        mapper.insertPasswordHistory(UuidV7Generator.generate("MPH"), memberId, encoded, now);

        // 3) DB 템플릿 기반 메일 발송 — 실패 시 트랜잭션 롤백
        try {
            Map<String, Object> model = new java.util.HashMap<>();
            model.put("loginId",      member.getLoginId());
            model.put("memberName",   member.getMemberName());
            model.put("tempPassword", tempPassword);
            model.put("sentAt",       now);
            mailService.sendFromTemplate(MailTemplate.CODE_PASSWORD_RESET, email, model);
        } catch (Exception e) {
            // 평문 비밀번호를 예외 메시지에 포함 금지
            throw new IllegalStateException("임시 비밀번호 메일 발송에 실패했습니다. 잠시 후 다시 시도하세요.", e);
        }
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = com.gonet.config.datasource.PrimaryDataSourceConfig.TRANSACTION_MGR)
    public void adminSoftDelete(String memberId, String reason) {
        Member existing = mapper.findById(memberId);
        if (existing == null) throw new IllegalArgumentException("회원을 찾을 수 없습니다.");

        // 관리자 강제 탈퇴 — 사유 자유 텍스트 5자 이상 필수.
        String trimmedReason = reason == null ? "" : reason.trim();
        if (trimmedReason.length() < 5) {
            throw new IllegalArgumentException("강제 탈퇴 사유는 5자 이상 입력해 주세요.");
        }
        // status=ADMIN_FORCE 고정, reason=사용자 입력 자유 텍스트
        WithdrawReasons.Normalized n = new WithdrawReasons.Normalized("ADMIN_FORCE", trimmedReason);

        mapper.insertWithdraw(existing, n.category(), n.note(),
            LocalDateTime.now().plusYears(WITHDRAW_RETENTION_YRS));
        int affected = mapper.softDelete(memberId);
        if (affected == 0) throw new IllegalArgumentException("이미 삭제된 회원입니다.");

        writeWithdrawAudit("MEMBER_FORCE_DELETE", "tb_member_withdraw",
            memberId, "MEMBER", existing.getLoginId(), n);
    }

    // ------------------------------------------------------------------
    private void recordConsent(String memberId, String type, String agreeYn,
                                String clientIp, String userAgent, LocalDateTime at) {
        MemberConsent c = new MemberConsent();
        c.setMemberConsentId(UuidV7Generator.generate("MCS"));
        c.setMemberId(memberId);
        c.setConsentType(type);
        c.setConsentVersion(CONSENT_VERSION);
        c.setAgreeYn(agreeYn == null ? "N" : agreeYn);
        c.setAgreedAt(at);
        c.setClientIp(clientIp);
        c.setUserAgent(userAgent);
        consentMapper.insert(c);
    }

    /**
     * 이벤트성 메일 발송 — 실패해도 주 트랜잭션은 유지 (가입/탈퇴 등은 메일 성공 여부와 무관).
     * 평문 PII 가 model 에 있어도 로그에는 수신자/템플릿코드만 남긴다.
     */
    private void sendTemplate(String templateCode, String to, Map<String, Object> model) {
        if (to == null || to.isBlank()) {
            log.warn("MAIL skip (no recipient) template={}", templateCode);
            return;
        }
        try {
            mailService.sendFromTemplate(templateCode, to, model);
        } catch (Exception ex) {
            log.warn("MAIL send-fail template={} to={} reason={}", templateCode, to, ex.getMessage());
        }
    }

    private static MemberSearch normalize(MemberSearch s) {
        return s == null ? new MemberSearch() : s;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    /** 모바일 브라우저 식별 정규식 — userAgent 에 흔히 나타나는 토큰. */
    private static final java.util.regex.Pattern MOBILE_UA = java.util.regex.Pattern.compile(
        "(?i)Mobile|Android|iPhone|iPad|iPod|Windows Phone|BlackBerry|Opera Mini|IEMobile");

    /** CHECK 제약 화이트리스트 — tb_member.chk_member_join_type. */
    private static final Set<String> ALLOWED_JOIN_TYPES = Set.of(
        "EMAIL", "KAKAO", "NAVER", "APPLE", "GOOGLE", "IDP", "WEB", "HOMEPAGE", "MOBILE");

    /**
     * tb_member.join_type 결정.
     *
     * <ol>
     *   <li>{@code joinSess.joinType} 가 명시되어 있고 화이트리스트 안이면 그대로 사용
     *       (OAuth2 경로 — provider 명 KAKAO/NAVER/GOOGLE/APPLE)</li>
     *   <li>그 외 NICE 홈페이지 가입 경로 — userAgent 의 모바일 토큰 검사로
     *       {@code MOBILE} vs {@code HOMEPAGE} 자동 결정</li>
     * </ol>
     */
    static String resolveJoinType(JoinSessionData joinSess, String userAgent) {
        if (joinSess != null) {
            String j = joinSess.getJoinType();
            if (j != null && !j.isBlank()) {
                String up = j.trim().toUpperCase();
                if (ALLOWED_JOIN_TYPES.contains(up)) return up;
            }
        }
        if (userAgent != null && MOBILE_UA.matcher(userAgent).find()) {
            return "MOBILE";
        }
        return "HOMEPAGE";
    }

    /**
     * birth_date (yyyy-MM-dd 또는 yyyyMMdd) 에서 YYYY 4자리 추출.
     * 연령대 통계용 — PIPA 일반 개인정보 (암호화 의무 X, 평문 저장 합법).
     *
     * @return 4자리 연도 String 또는 null (입력 형식 오류/빈 값)
     */
    static String extractBirthYear(String birthDate) {
        if (birthDate == null) return null;
        String s = birthDate.trim();
        if (s.length() < 4) return null;
        String year = s.substring(0, 4);
        // 숫자 4자리만 허용 (NICE 본인인증은 yyyyMMdd 8자리, 사용자 입력은 yyyy-MM-dd)
        for (int i = 0; i < 4; i++) {
            if (!Character.isDigit(year.charAt(i))) return null;
        }
        return year;
    }
}
