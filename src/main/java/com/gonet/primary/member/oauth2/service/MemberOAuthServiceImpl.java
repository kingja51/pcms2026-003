package com.gonet.primary.member.oauth2.service;

import com.gonet.common.audit.AuditEvent;
import com.gonet.common.audit.AuditLogger;
import com.gonet.common.util.UuidV7Generator;
import com.gonet.config.datasource.PrimaryDataSourceConfig;
import com.gonet.primary.member.dto.JoinSessionData;
import com.gonet.primary.member.dto.MemberJoinForm;
import com.gonet.primary.member.oauth2.dto.ExternalProfile;
import com.gonet.primary.member.oauth2.dto.MemberOAuth;
import com.gonet.primary.member.oauth2.mapper.MemberOAuthMapper;
import com.gonet.primary.member.service.MemberService;
import com.gonet.primary.system.login.dto.UserLogin;
import com.gonet.primary.system.login.mapper.UserLoginMapper;
import org.egovframe.rte.fdl.cmmn.EgovAbstractServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * {@link MemberOAuthService} 구현체.
 *
 * <p>매핑 mapper / v_user_login mapper 위임만 담당. 외부 API 호출 / 감사 이벤트는
 * 호출 측(컨트롤러) 또는 별도 어드바이저 책임 — 본 서비스는 단순 위임.
 *
 * <p>트랜잭션 정책 — 클래스 단 readOnly=true 기본, write 메서드(link/updateLastLogin)
 * 만 readOnly=false 오버라이드. {@link com.gonet.primary.member.service.MemberServiceImpl}
 * 와 동일 패턴.
 */
@Service
@Transactional(readOnly = true, transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
public class MemberOAuthServiceImpl extends EgovAbstractServiceImpl implements MemberOAuthService {

    /** {@code v_user_login} user_type 화이트리스트 — 회원 자동 로그인 전용. */
    private static final List<String> MEMBER_TYPES = List.of("MEMBER");

    private final MemberOAuthMapper oauthMapper;
    private final UserLoginMapper   userLoginMapper;
    private final MemberService     memberService;
    private final AuditLogger       auditLogger;

    public MemberOAuthServiceImpl(MemberOAuthMapper oauthMapper,
                                   UserLoginMapper userLoginMapper,
                                   MemberService memberService,
                                   AuditLogger auditLogger) {
        this.oauthMapper     = oauthMapper;
        this.userLoginMapper = userLoginMapper;
        this.memberService   = memberService;
        this.auditLogger     = auditLogger;
    }

    @Override
    public MemberOAuth findByProviderUser(String provider, String providerUserId) {
        if (provider == null || providerUserId == null) return null;
        return oauthMapper.findByProviderUser(provider, providerUserId);
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public void link(MemberOAuth link) {
        oauthMapper.insert(link);
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public void updateLastLogin(String memberOauthId) {
        if (memberOauthId == null || memberOauthId.isBlank()) return;
        oauthMapper.updateLastLogin(memberOauthId, LocalDateTime.now());
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public void deactivate(String memberOauthId) {
        if (memberOauthId == null || memberOauthId.isBlank()) return;
        int affected = oauthMapper.softDeleteById(memberOauthId);
        if (affected > 0) {
            auditLogger.write(AuditEvent.of("OAUTH2_MAPPING_DEACTIVATE", "tb_member_oauth")
                .withTarget(memberOauthId)
                .withResult("SUCCESS")
                .withAfter("{\"reason\":\"member_gone\"}"));
        }
    }

    @Override
    public UserLogin loadMemberUserLogin(String loginId) {
        if (loginId == null || loginId.isBlank()) return null;
        return userLoginMapper.findByLoginIdInTypes(loginId, MEMBER_TYPES);
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public String joinAndLink(MemberJoinForm form,
                                JoinSessionData sess,
                                ExternalProfile profile,
                                String siteId,
                                String clientIp,
                                String userAgent) {
        // 1) 회원 가입 — Propagation.REQUIRED 로 본 트랜잭션에 합류. 실패 시 그대로 throw.
        String memberId = memberService.join(form, sess, siteId, clientIp, userAgent);

        // 2) OAuth 매핑 — UNIQUE (provider, provider_user_id) 가 delete_yn 무관이므로
        //    stale soft-deleted row 가 있으면 reactivate, 없으면 INSERT 로 분기.
        LocalDateTime now = LocalDateTime.now();
        String providerName = profile.getProvider().name();
        MemberOAuth stale = oauthMapper.findAnyByProviderUser(providerName, profile.getProviderUserId());

        String memberOauthId;
        String auditAction;
        if (stale != null) {
            // 재가입 경로 — 기존 row 를 새 회원으로 reactivate
            memberOauthId = stale.getMemberOauthId();
            oauthMapper.reactivate(memberOauthId, memberId,
                profile.getEmail(), profile.getName(), now);
            auditAction = "OAUTH2_RELINK";
        } else {
            MemberOAuth link = new MemberOAuth();
            link.setMemberOauthId(UuidV7Generator.generate("MOA"));
            link.setMemberId(memberId);
            link.setProvider(providerName);
            link.setProviderUserId(profile.getProviderUserId());
            link.setEmailAtLink(profile.getEmail());
            link.setNameAtLink(profile.getName());
            link.setLinkedAt(now);
            link.setLastLoginAt(now);
            link.setUseYn("Y");
            link.setDeleteYn("N");
            oauthMapper.insert(link);
            memberOauthId = link.getMemberOauthId();
            auditAction = "OAUTH2_LINK";
        }

        // 3) 감사 이벤트 — 트랜잭션 내 발행. log_audit INSERT 도 같이 묶이므로 부분 실패 없음.
        auditLogger.write(AuditEvent.of(auditAction, "tb_member_oauth")
            .withActor(memberId, "MEMBER", form.getLoginId())
            .withTarget(memberOauthId)
            .withResult("SUCCESS")
            .withAfter("{\"provider\":\"" + providerName + "\"}"));

        return memberId;
    }
}
