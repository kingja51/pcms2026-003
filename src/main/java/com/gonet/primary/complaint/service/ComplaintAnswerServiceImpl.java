package com.gonet.primary.complaint.service;

import com.gonet.common.audit.AuditEvent;
import com.gonet.common.audit.AuditLogger;
import com.gonet.common.util.UuidV7Generator;
import com.gonet.config.datasource.PrimaryDataSourceConfig;
import org.springframework.security.access.AccessDeniedException;
import com.gonet.primary.complaint.dto.ComplaintAnswer;
import com.gonet.primary.complaint.dto.ComplaintAnswerSaveForm;
import com.gonet.primary.complaint.dto.ComplaintStatus;
import com.gonet.primary.complaint.mapper.ComplaintAnswerMapper;
import com.gonet.primary.complaint.mapper.ComplaintArticleMapper;
import org.egovframe.rte.fdl.cmmn.EgovAbstractServiceImpl;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true,
    transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
public class ComplaintAnswerServiceImpl extends EgovAbstractServiceImpl
        implements ComplaintAnswerService {

    private final ComplaintAnswerMapper  answerMapper;
    private final ComplaintArticleMapper articleMapper;
    private final AuditLogger            auditLogger;

    public ComplaintAnswerServiceImpl(ComplaintAnswerMapper answerMapper,
                                      ComplaintArticleMapper articleMapper,
                                      AuditLogger auditLogger) {
        this.answerMapper  = answerMapper;
        this.articleMapper = articleMapper;
        this.auditLogger   = auditLogger;
    }

    @Override
    public List<ComplaintAnswer> listByArticle(String articleId) {
        return answerMapper.findByArticleId(articleId);
    }

    @Override
    public ComplaintAnswer get(String answerId) {
        return answerMapper.findById(answerId);
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public String create(ComplaintAnswerSaveForm form, Authentication auth) {
        ComplaintAnswer answer = new ComplaintAnswer();
        answer.setAnswerId(UuidV7Generator.generate());
        answer.setArticleId(form.getArticleId());
        answer.setContent(form.getContent());
        answer.setIsFinal(form.getIsFinal());

        // answerer_id/type/name 은 전부 NOT NULL — 미인증 호출이면 매퍼 INSERT 가 500 으로
        // 실패하므로 명시적으로 선차단(관리자 답변은 인증 필수 경로라 정상 흐름엔 영향 없음).
        if (auth == null) {
            throw new AccessDeniedException("답변 등록에는 인증이 필요합니다.");
        }
        // 답변자 정보 — answerer_name 은 NOT NULL 컬럼인데 원본은 미세팅(잠재 버그,
        // 원본 운영 DB 의 답변 테이블이 비어 있어 미노출). principal 에 표시명 필드가
        // 없으므로 loginId 를 이름 폴백으로, 부서명은 있으면 채운다.
        answer.setAnswererId(auth.getName());
        answer.setAnswererType(resolveAnswererType(auth));
        answer.setAnswererName(auth.getName());
        if (auth.getPrincipal() instanceof com.gonet.primary.system.login.dto.CustomUserDetails ud) {
            answer.setAnswererDept(ud.getDepartmentName());
        }

        answerMapper.insert(answer);
        articleMapper.incrementAnswerCount(form.getArticleId());

        // 최종 답변 시 → 민원 상태 ANSWERED + answered_at 갱신
        if ("Y".equalsIgnoreCase(form.getIsFinal())) {
            articleMapper.updateStatus(form.getArticleId(), ComplaintStatus.ANSWERED.name());
            articleMapper.updateAnsweredAt(form.getArticleId());
        } else {
            // 중간 답변이라도 IN_PROGRESS 로 전환 (RECEIVED 상태일 때만)
            var article = articleMapper.findById(form.getArticleId());
            if (article != null && ComplaintStatus.RECEIVED.name().equals(article.getStatus())) {
                articleMapper.updateStatus(form.getArticleId(), ComplaintStatus.IN_PROGRESS.name());
            }
        }

        auditLogger.write(AuditEvent.of("COMPLAINT_ANSWER_CREATE", "tb_complaint_answer")
            .withTarget(answer.getAnswerId())
            .withAfter("{\"articleId\":\"" + form.getArticleId()
                     + "\",\"isFinal\":\"" + answer.getIsFinal() + "\"}"));
        return answer.getAnswerId();
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public void update(String answerId, ComplaintAnswerSaveForm form) {
        ComplaintAnswer answer = answerMapper.findById(answerId);
        if (answer == null) throw new IllegalArgumentException("답변을 찾을 수 없습니다.");
        answer.setContent(form.getContent());
        answer.setIsFinal(form.getIsFinal());
        answerMapper.update(answer);

        // 최종 답변 여부 변경 시 민원 상태 동기화
        if ("Y".equalsIgnoreCase(form.getIsFinal())) {
            articleMapper.updateStatus(answer.getArticleId(), ComplaintStatus.ANSWERED.name());
            articleMapper.updateAnsweredAt(answer.getArticleId());
        }

        auditLogger.write(AuditEvent.of("COMPLAINT_ANSWER_UPDATE", "tb_complaint_answer")
            .withTarget(answerId));
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public void delete(String answerId) {
        answerMapper.softDelete(answerId);
        auditLogger.write(AuditEvent.of("COMPLAINT_ANSWER_DELETE", "tb_complaint_answer")
            .withTarget(answerId));
    }

    /**
     * answerer_type 해석 — CHECK 제약은 ('ADMIN','EMPLOYEE','STAFF') 만 허용.
     * 원본은 ROLE_ 프리픽스 붙은 권한 문자열("ROLE_ADMIN")/폴백 "MEMBER" 를 그대로
     * 반환해 전 케이스가 제약 위반이던 잠재 버그 — principal 의 userType(3주체)으로
     * 해석하고, 권한 문자열은 프리픽스를 벗겨 보조 판별한다.
     */
    private String resolveAnswererType(Authentication auth) {
        if (auth.getPrincipal() instanceof com.gonet.primary.system.login.dto.CustomUserDetails ud) {
            String ut = ud.getUserType();
            if ("ADMIN".equalsIgnoreCase(ut))    return "ADMIN";
            if ("EMPLOYEE".equalsIgnoreCase(ut)) return "EMPLOYEE";
        }
        return auth.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .map(a -> a.startsWith("ROLE_") ? a.substring(5) : a)
            .filter(a -> a.equals("ADMIN") || a.equals("STAFF") || a.equals("EMPLOYEE"))
            .findFirst()
            .orElse("STAFF");
    }
}
