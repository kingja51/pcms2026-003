package com.gonet.primary.complaint.controller;

import com.gonet.common.captcha.CaptchaGuard;
import com.gonet.common.dto.PageResponse;
import com.gonet.common.util.IpUtils;
import com.gonet.common.util.UuidV7Generator;
import com.gonet.primary.complaint.dto.*;
import com.gonet.primary.complaint.service.*;
import com.gonet.primary.system.login.dto.CustomUserDetails;
import com.gonet.primary.system.site.service.SiteContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 민원 사용자 Controller — 목록/작성/상세/종결.
 *
 * <p>URL prefix: {@code /complaint/{masterCode}}
 * siteCode 는 master 에서 조회하여 사용한다.
 */
@Controller
@RequestMapping("/complaint/{masterCode}")
public class ComplaintUsrController {

    private static final Logger log = LoggerFactory.getLogger(ComplaintUsrController.class);

    private final ComplaintMasterService   masterService;
    private final ComplaintArticleService  articleService;
    private final ComplaintAnswerService   answerService;
    private final ComplaintCategoryService categoryService;
    private final SiteContextResolver      siteContextResolver;
    private final CaptchaGuard             captchaGuard;

    public ComplaintUsrController(ComplaintMasterService masterService,
                                  ComplaintArticleService articleService,
                                  ComplaintAnswerService answerService,
                                  ComplaintCategoryService categoryService,
                                  SiteContextResolver siteContextResolver,
                                  CaptchaGuard captchaGuard) {
        this.masterService       = masterService;
        this.articleService      = articleService;
        this.answerService       = answerService;
        this.categoryService     = categoryService;
        this.siteContextResolver = siteContextResolver;
        this.captchaGuard        = captchaGuard;
    }

    @ModelAttribute
    public void injectSiteContext(@PathVariable(required = false) String masterCode,
                                  HttpServletRequest req,
                                  Model model) {
        if (masterCode == null || masterCode.isBlank()) return;
        ComplaintMaster master = masterService.getByMasterCode(masterCode);
        if (master == null || !"Y".equalsIgnoreCase(master.getUseYn())) return;
        siteContextResolver.injectByCodeAndStick(master.getSiteCode(), req, model);
    }

    // ══════════════════════════════════════════════════════════════════
    // 목록
    // ══════════════════════════════════════════════════════════════════

    @GetMapping
    public String list(@PathVariable String masterCode,
                       @ModelAttribute("search") ComplaintArticleSearch search,
                       Model model, RedirectAttributes ra,
                       Authentication auth) {
        ComplaintMaster master = resolveMaster(masterCode, ra);
        if (master == null) return "redirect:/";

        search.setComplaintMasterId(master.getComplaintMasterId());

        boolean publicMode = "Y".equalsIgnoreCase(master.getShowInList());
        if (!publicMode) {
            if (auth != null && auth.isAuthenticated()) {
                search.setWriterMemberId(auth.getName());
            } else {
                search.setWriterMemberId("__NO_MATCH__");
            }
        }

        model.addAttribute("categories", categoryService.listByMaster(master.getComplaintMasterId()));
        PageResponse<ComplaintArticle> page = articleService.listForUser(search);
        model.addAttribute("page", page);
        model.addAttribute("master", master);
        model.addAttribute("canWrite", canWrite(master, auth));
        model.addAttribute("search", search);
        return "front/complaint/list";
    }

    // ══════════════════════════════════════════════════════════════════
    // 작성
    // ══════════════════════════════════════════════════════════════════

    @GetMapping("/write")
    public String writeForm(@PathVariable String masterCode,
                            Model model, RedirectAttributes ra,
                            Authentication auth) {
        ComplaintMaster master = resolveMaster(masterCode, ra);
        if (master == null) return "redirect:/";
        if (!canWrite(master, auth)) {
            ra.addFlashAttribute("error", "민원을 작성하려면 로그인이 필요합니다.");
            return "redirect:/member/login";
        }
        if (!model.containsAttribute("form")) {
            ComplaintArticleSaveForm form = new ComplaintArticleSaveForm();
            form.setComplaintMasterId(master.getComplaintMasterId());
            String articleId = UuidV7Generator.generate();
            form.setArticleId(articleId);
            prefillWriter(form, auth);
            model.addAttribute("form", form);
            model.addAttribute("articleId", articleId);
        }
        model.addAttribute("master", master);
        model.addAttribute("categories", categoryService.listByMaster(master.getComplaintMasterId()));
        return "front/complaint/write";
    }

    @PostMapping("/write")
    public String write(@PathVariable String masterCode,
                        @Valid @ModelAttribute("form") ComplaintArticleSaveForm form,
                        BindingResult br,
                        HttpServletRequest req,
                        Model model, RedirectAttributes ra,
                        Authentication auth) {
        ComplaintMaster master = resolveMaster(masterCode, ra);
        if (master == null) return "redirect:/";
        if (!canWrite(master, auth)) {
            ra.addFlashAttribute("error", "민원을 작성하려면 로그인이 필요합니다.");
            return "redirect:/member/login";
        }
        form.setComplaintMasterId(master.getComplaintMasterId());
        prefillWriter(form, auth);

        if (br.hasErrors()) {
            model.addAttribute("form", form);
            model.addAttribute("master", master);
            model.addAttribute("categories", categoryService.listByMaster(master.getComplaintMasterId()));
            return "front/complaint/write";
        }
        // CAPTCHA — 마스터에서 토글된 경우만 (master.captchaYn='Y'). 민원 도메인은 SNS/NICE 만으로
        // 작성 가능해 봇 자동 글 작성 위험이 일반 게시판보다 높음. 운영 권장 'Y'.
        if ("Y".equalsIgnoreCase(master.getCaptchaYn()) && !captchaGuard.verifyOrFail(req)) {
            log.warn("COMPLAINT_WRITE captcha_fail masterCode={} ip={}", masterCode, IpUtils.clientIp(req));
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", "보안 검증에 실패했습니다. 다시 시도해 주세요.");
            return "redirect:/complaint/" + masterCode + "/write";
        }
        try {
            String complaintNo = articleService.submit(master, form, auth);
            log.info("COMPLAINT_SUBMIT ok no={}", complaintNo);
            ra.addFlashAttribute("complaintNo", complaintNo);
            ra.addFlashAttribute("message", "민원이 접수되었습니다. 접수번호: " + complaintNo);
            return "redirect:/complaint/" + masterCode;
        } catch (Exception ex) {
            log.warn("COMPLAINT_SUBMIT error masterCode={}", masterCode, ex);
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", "접수 중 오류가 발생했습니다.");
            return "redirect:/complaint/" + masterCode + "/write";
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 상세 조회
    // ══════════════════════════════════════════════════════════════════

    @GetMapping("/{complaintNo}")
    public String detail(@PathVariable String masterCode,
                         @PathVariable String complaintNo,
                         Model model, RedirectAttributes ra,
                         Authentication auth) {
        ComplaintMaster master = resolveMaster(masterCode, ra);
        if (master == null) return "redirect:/";

        ComplaintArticle article = articleService.getByComplaintNo(
                master.getComplaintMasterId(), complaintNo);
        if (article == null) {
            ra.addFlashAttribute("error", "민원을 찾을 수 없습니다.");
            return "redirect:/complaint/" + masterCode;
        }
        if (!articleService.isOwnerOrStaff(article, auth)) {
            ra.addFlashAttribute("error", "접근 권한이 없습니다.");
            return "redirect:/complaint/" + masterCode;
        }
        model.addAttribute("master", master);
        model.addAttribute("article", article);
        model.addAttribute("answers", answerService.listByArticle(article.getArticleId()));
        model.addAttribute("existingFiles", articleService.listAttachments(article.getFileGroupId()));
        model.addAttribute("canClose",
            "ANSWERED".equals(article.getStatus()) || "IN_PROGRESS".equals(article.getStatus()));
        String pid = principalId(auth);
        boolean isOwner = pid != null
            && article.getCreatedBy() != null
            && article.getCreatedBy().equals(pid);
        boolean canEdit = isOwner && article.getAnswerCount() == 0;
        model.addAttribute("isOwner", isOwner);
        model.addAttribute("canEdit", canEdit);
        return "front/complaint/detail";
    }

    // ══════════════════════════════════════════════════════════════════
    // 수정
    // ══════════════════════════════════════════════════════════════════

    @GetMapping("/{complaintNo}/edit")
    public String editForm(@PathVariable String masterCode,
                           @PathVariable String complaintNo,
                           Model model, RedirectAttributes ra,
                           Authentication auth) {
        ComplaintMaster master = resolveMaster(masterCode, ra);
        if (master == null) return "redirect:/";

        ComplaintArticle article = articleService.getByComplaintNo(
                master.getComplaintMasterId(), complaintNo);
        if (article == null) {
            ra.addFlashAttribute("error", "민원을 찾을 수 없습니다.");
            return "redirect:/complaint/" + masterCode;
        }
        String pid = principalId(auth);
        if (pid == null || !pid.equals(article.getCreatedBy())) {
            ra.addFlashAttribute("error", "접근 권한이 없습니다.");
            return "redirect:/complaint/" + masterCode + "/" + complaintNo;
        }
        if (article.getAnswerCount() > 0) {
            ra.addFlashAttribute("error", "답변이 등록된 민원은 수정할 수 없습니다.");
            return "redirect:/complaint/" + masterCode + "/" + complaintNo;
        }
        if (!model.containsAttribute("form")) {
            ComplaintArticleSaveForm form = new ComplaintArticleSaveForm();
            form.setComplaintMasterId(master.getComplaintMasterId());
            form.setArticleId(article.getArticleId());
            form.setCategoryId(article.getCategoryId());
            form.setWriterName(article.getWriterName());
            form.setWriterContact(article.getWriterContactEnc());
            form.setTitle(article.getTitle());
            form.setContent(article.getContent());
            model.addAttribute("form", form);
        }
        model.addAttribute("master", master);
        model.addAttribute("article", article);
        model.addAttribute("articleId", article.getArticleId());
        model.addAttribute("categories", categoryService.listByMaster(master.getComplaintMasterId()));
        model.addAttribute("existingFiles", articleService.listAttachments(article.getFileGroupId()));
        model.addAttribute("editMode", true);
        return "front/complaint/write";
    }

    @PostMapping("/{complaintNo}/edit")
    public String edit(@PathVariable String masterCode,
                       @PathVariable String complaintNo,
                       @Valid @ModelAttribute("form") ComplaintArticleSaveForm form,
                       BindingResult br,
                       HttpServletRequest req,
                       Model model, RedirectAttributes ra,
                       Authentication auth) {
        ComplaintMaster master = resolveMaster(masterCode, ra);
        if (master == null) return "redirect:/";

        ComplaintArticle article = articleService.getByComplaintNo(
                master.getComplaintMasterId(), complaintNo);
        if (article == null) {
            ra.addFlashAttribute("error", "민원을 찾을 수 없습니다.");
            return "redirect:/complaint/" + masterCode;
        }

        if (br.hasErrors()) {
            model.addAttribute("form", form);
            model.addAttribute("master", master);
            model.addAttribute("article", article);
            model.addAttribute("articleId", article.getArticleId());
            model.addAttribute("categories", categoryService.listByMaster(master.getComplaintMasterId()));
            model.addAttribute("existingFiles", articleService.listAttachments(article.getFileGroupId()));
            model.addAttribute("editMode", true);
            return "front/complaint/write";
        }
        // CAPTCHA — 마스터 토글 기준. 수정 시에도 동일 강제.
        if ("Y".equalsIgnoreCase(master.getCaptchaYn()) && !captchaGuard.verifyOrFail(req)) {
            log.warn("COMPLAINT_EDIT captcha_fail masterCode={} no={} ip={}",
                masterCode, complaintNo, IpUtils.clientIp(req));
            ra.addFlashAttribute("error", "보안 검증에 실패했습니다. 다시 시도해 주세요.");
            return "redirect:/complaint/" + masterCode + "/" + complaintNo + "/edit";
        }
        try {
            articleService.update(master, article.getArticleId(), form, auth);
            ra.addFlashAttribute("message", "민원이 수정되었습니다.");
            return "redirect:/complaint/" + masterCode + "/" + complaintNo;
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/complaint/" + masterCode + "/" + complaintNo + "/edit";
        } catch (Exception ex) {
            log.warn("COMPLAINT_EDIT error no={}", complaintNo, ex);
            ra.addFlashAttribute("error", "수정 중 오류가 발생했습니다.");
            return "redirect:/complaint/" + masterCode + "/" + complaintNo + "/edit";
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 삭제
    // ══════════════════════════════════════════════════════════════════

    @PostMapping("/{complaintNo}/delete")
    public String delete(@PathVariable String masterCode,
                         @PathVariable String complaintNo,
                         RedirectAttributes ra, Authentication auth) {
        ComplaintMaster master = resolveMaster(masterCode, ra);
        if (master == null) return "redirect:/";

        ComplaintArticle article = articleService.getByComplaintNo(
                master.getComplaintMasterId(), complaintNo);
        if (article == null) {
            ra.addFlashAttribute("error", "민원을 찾을 수 없습니다.");
            return "redirect:/complaint/" + masterCode;
        }
        String pid = principalId(auth);
        if (pid == null || !pid.equals(article.getCreatedBy())) {
            ra.addFlashAttribute("error", "접근 권한이 없습니다.");
            return "redirect:/complaint/" + masterCode + "/" + complaintNo;
        }
        if (article.getAnswerCount() > 0) {
            ra.addFlashAttribute("error", "답변이 등록된 민원은 삭제할 수 없습니다.");
            return "redirect:/complaint/" + masterCode + "/" + complaintNo;
        }
        try {
            articleService.delete(article.getArticleId());
            ra.addFlashAttribute("message", "민원이 삭제되었습니다.");
        } catch (Exception ex) {
            log.warn("COMPLAINT_DELETE error no={}", complaintNo, ex);
            ra.addFlashAttribute("error", "삭제 중 오류가 발생했습니다.");
            return "redirect:/complaint/" + masterCode + "/" + complaintNo;
        }
        return "redirect:/complaint/" + masterCode;
    }

    // ══════════════════════════════════════════════════════════════════
    // 종결 (민원인이 직접 종결)
    // ══════════════════════════════════════════════════════════════════

    @PostMapping("/{complaintNo}/close")
    public String close(@PathVariable String masterCode,
                        @PathVariable String complaintNo,
                        RedirectAttributes ra, Authentication auth) {
        ComplaintMaster master = resolveMaster(masterCode, ra);
        if (master == null) return "redirect:/";

        ComplaintArticle article = articleService.getByComplaintNo(
                master.getComplaintMasterId(), complaintNo);
        if (article == null) {
            ra.addFlashAttribute("error", "민원을 찾을 수 없습니다.");
            return "redirect:/complaint/" + masterCode;
        }
        try {
            articleService.close(article.getArticleId(), auth);
            ra.addFlashAttribute("message", "민원이 종결 처리되었습니다.");
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        } catch (Exception ex) {
            log.warn("COMPLAINT_CLOSE error no={}", complaintNo, ex);
            ra.addFlashAttribute("error", "처리 중 오류가 발생했습니다.");
        }
        return "redirect:/complaint/" + masterCode + "/" + complaintNo;
    }

    // ══════════════════════════════════════════════════════════════════
    // Helpers
    // ══════════════════════════════════════════════════════════════════

    private ComplaintMaster resolveMaster(String masterCode, RedirectAttributes ra) {
        ComplaintMaster master = masterService.getByMasterCode(masterCode);
        if (master == null || !"Y".equalsIgnoreCase(master.getUseYn())) {
            if (ra != null) ra.addFlashAttribute("error", "민원 게시판을 찾을 수 없습니다.");
            return null;
        }
        return master;
    }

    /** AuditContext.userId()와 동일하게 CustomUserDetails.userId(UUID PK)를 우선 반환. */
    private static String principalId(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) return null;
        Object principal = auth.getPrincipal();
        if (principal instanceof CustomUserDetails ud) return ud.getUserId();
        return auth.getName();
    }

    private boolean canWrite(ComplaintMaster master, Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) return false;
        if ("Y".equalsIgnoreCase(master.getAllowMemberYn())) {
            return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_MEMBER") || a.equals("ROLE_EMPLOYEE")
                            || a.equals("ROLE_STAFF") || a.equals("ROLE_ADMIN"));
        }
        return false;
    }

    private void prefillWriter(ComplaintArticleSaveForm form, Authentication auth) {
        if (auth == null) return;
        form.setWriterAuthType(WriterAuthType.MEMBER.name());
        form.setWriterMemberId(auth.getName());
    }
}
