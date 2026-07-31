package com.gonet.primary.board.article.controller;

import com.gonet.common.captcha.CaptchaGuard;
import com.gonet.common.dto.PageResponse;
import com.gonet.common.util.IpUtils;
import com.gonet.common.util.UuidV7Generator;
import com.gonet.primary.board.article.dto.BbsArticle;
import com.gonet.primary.board.article.dto.BbsArticleSaveForm;
import com.gonet.primary.board.article.dto.BbsArticleSearch;
import com.gonet.primary.board.article.dto.BbsArticleStatus;
import com.gonet.primary.board.article.service.BoardArticleService;
import com.gonet.primary.board.category.service.BoardCategoryService;
import com.gonet.primary.board.comment.service.BoardCommentService;
import com.gonet.primary.board.like.dto.BbsLikeTarget;
import com.gonet.primary.board.like.service.BoardLikeService;
import com.gonet.primary.board.master.dto.BbsMaster;
import com.gonet.primary.board.master.dto.BbsType;
import com.gonet.primary.board.master.service.BoardMasterService;
import com.gonet.primary.banner.service.BannerService;
import com.gonet.primary.popup.service.PopupService;
import com.gonet.primary.system.login.dto.CustomUserDetails;
import com.gonet.primary.system.site.service.SiteContextResolver;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.gonet.primary.file.dto.FileItem;

/**
 * 사용자 게시판 화면.
 *
 * <p>URL: {@code /bbs/{siteCode}/{bbsCode}} 와 그 하위.
 * <p>레이아웃은 SiteContextInterceptor 가 layoutPath 를 모델에 주입 — 사이트별 레이아웃 자동 decorate.
 *
 * <p>view_count 중복 방지: 쿠키 {@code bbs_v_{articleId}} 30분 TTL — 동일 브라우저
 * 재방문 시 increment skip.
 */
@Controller
@RequestMapping("/bbs/{siteCode}/{bbsCode}")
public class BoardUsrController {

    private static final Logger log = LoggerFactory.getLogger(BoardUsrController.class);

    private static final int VIEW_COUNT_COOKIE_TTL_SECONDS = 30 * 60; // 30 분

    private final BoardMasterService   masterService;
    private final BoardArticleService  articleService;
    private final BoardCommentService  commentService;
    private final BoardCategoryService categoryService;
    private final BoardLikeService     likeService;
    private final SiteContextResolver siteContextResolver;
    private final CaptchaGuard captchaGuard;
    private final PopupService  popupService;
    private final BannerService bannerService;


    public BoardUsrController(BoardMasterService masterService,
                              BoardArticleService articleService,
                              BoardCommentService commentService,
                              BoardCategoryService categoryService,
                              BoardLikeService likeService,
                              SiteContextResolver siteContextResolver,
                              CaptchaGuard captchaGuard,
                              PopupService popupService,
                              BannerService bannerService) {
        this.masterService   = masterService;
        this.articleService  = articleService;
        this.commentService  = commentService;
        this.categoryService = categoryService;
        this.likeService     = likeService;
        this.siteContextResolver = siteContextResolver;
        this.captchaGuard = captchaGuard;
        this.popupService  = popupService;
        this.bannerService = bannerService;
    }

    @ModelAttribute
    public void injectSiteContext(@PathVariable(required = false) String siteCode,
                                  @PathVariable(required = false) String bbsCode,
                                  HttpServletRequest req,
                                  Model model) {
        if (siteCode == null || siteCode.isBlank()
            || bbsCode == null || bbsCode.isBlank()) return;

        BbsMaster master = masterService.getBySiteCodeAndBbsCode(siteCode, bbsCode);
        if (master == null) {
            siteContextResolver.injectByCodeAndStick(siteCode, req, model);
            return;
        }

        model.addAttribute("master", master);
        model.addAttribute("bbsMasterId", master.getBbsMasterId());
        model.addAttribute("menuId", master.getMenuId());
        model.addAttribute("siteId", master.getSiteId());

        // 사이트 컨텍스트 주입 + session sticky — 이후 평면 URL 도메인이 동일 사이트 레이아웃 유지.
        siteContextResolver.injectByCodeAndStick(master.getSiteCode(), req, model);

        // 활성 팝업/배너 명시 주입 — SiteContextModelAdvice 는 빈 컬렉션 기본값만 제공
        // ("후속 PR 에서 컨트롤러별 명시 주입" 문서화 항목). 서비스 결과는 5분 캐시.
        model.addAttribute("activePopups",      popupService.findActive(master.getSiteId()));
        model.addAttribute("bannersByLocation", bannerService.findActiveByLocation(master.getSiteId()));
    }


    // ==================================================================
    // 목록
    // ==================================================================

    @GetMapping
    public String list(@PathVariable String siteCode,
                        @PathVariable String bbsCode,
                        @ModelAttribute("search") BbsArticleSearch search,
                        Model model) {
        BbsMaster master = resolveMaster(siteCode, bbsCode);
        ensureReadable(master);

        // 사용자 화면은 PUBLISHED 만 노출
        search.setBbsMasterId(master.getBbsMasterId());
        search.setStatus(BbsArticleStatus.PUBLISHED.name());

        // 통합 게시판 모드. 구분자 콤마
        if(master.isAggregator() && master.getGroupedBoardIds() != null && master.getGroupedBoardIds().contains(",")) {
            String[] groupedBoardIds = master.getGroupedBoardIds().split(",");
            search.setGroupedBoardIdArray(groupedBoardIds);
        }

        List<BbsArticle> rows = articleService.search(search);
        int total = articleService.count(search);

        model.addAttribute("master", master);
        model.addAttribute("siteId",   master.getSiteId());
        model.addAttribute("siteCode", siteCode);
        model.addAttribute("bbsCode", bbsCode);
        model.addAttribute("menuId",   master.getMenuId());
        model.addAttribute("page",
            PageResponse.of(rows, search.getPage(), search.getPageSize(), total));
        model.addAttribute("canWrite", canWrite(master));
        model.addAttribute("categories",
            categoryService.listActive(master.getBbsMasterId()));

        // PDF 게시판 — 목록 아코디언에서 첨부 PDF 를 바로 임베드하도록 글별 첨부 사전 로드.
        // 다른 타입은 목록에서 첨부를 쓰지 않으므로 PDF 한정(불필요 쿼리 방지).
        if (BbsType.safeParse(master.getBbsType()) == BbsType.PDF) {
            Map<String, List<FileItem>> filesByArticle = new LinkedHashMap<>();
            for (BbsArticle row : rows) {
                if (row.getFileGroupId() != null && !row.getFileGroupId().isBlank()) {
                    filesByArticle.put(row.getArticleId(),
                        articleService.listAttachments(row.getFileGroupId()));
                }
            }
            model.addAttribute("filesByArticle", filesByArticle);
        }
        return viewPath(master, "list");
    }

    // ==================================================================
    // 상세
    // ==================================================================

    @GetMapping("/{articleId}")
    public String detail(@PathVariable String siteCode,
                          @PathVariable String bbsCode,
                          @PathVariable String articleId,
                          HttpServletRequest req,
                          HttpServletResponse res,
                          Model model,
                          RedirectAttributes ra) {
        BbsMaster master = resolveMaster(siteCode, bbsCode);
        ensureReadable(master);

        // BbsArticle article = articleService.get(master.getBbsMasterId(), articleId);
        BbsArticle article = articleService.getById(articleId);

        if (article == null
            || !BbsArticleStatus.PUBLISHED.name().equals(article.getStatus())) {
            ra.addFlashAttribute("error", "게시글을 찾을 수 없습니다.");
            return "redirect:/bbs/" + siteCode + "/" + bbsCode;
        }
        // 비밀글 본문 접근 제어
        boolean canSeeBody = !article.isSecret() || canSeeSecret(article);

        // view_count — 30분 쿠키 deduplication
        if (canSeeBody && shouldCountView(req, articleId)) {
            articleService.incrementViewCount(articleId);
            article.setViewCount(article.getViewCount() + 1);
            stampViewCookie(res, articleId);
        }

        model.addAttribute("master", master);
        model.addAttribute("article", article);
        model.addAttribute("canSeeBody", canSeeBody);
        boolean canEdit   = canEdit(article);
        boolean canDelete = canDelete(article);
        model.addAttribute("canEdit",   canEdit);
        model.addAttribute("canDelete", canDelete);
        // canManage(템플릿) = master.bbsMasterId == article.bbsMasterId — 불일치 시 수정/삭제 버튼 미노출.
        log.info("===BBS_USR_DETAIL_PERM articleId={} master.bbsMasterId={} article.bbsMasterId={} canEdit={} canDelete={} canSeeBody={}",
            articleId, master.getBbsMasterId(), article.getBbsMasterId(), canEdit, canDelete, canSeeBody);
        model.addAttribute("siteId",   master.getSiteId());
        model.addAttribute("siteCode", siteCode);
        model.addAttribute("bbsCode", bbsCode);
        model.addAttribute("menuId",   master.getMenuId());
        // 통합 게시판 뷰: master 가 aggregator 이고 글의 원본 게시판이 다를 때 true
        model.addAttribute("isAggregatorView",
            master.isAggregator() && !master.getBbsMasterId().equals(article.getBbsMasterId()));
        // B5 — 좋아요 상태 (현재 사용자가 눌렀는지)
        CustomUserDetails me = currentUser();
        model.addAttribute("articleLikedByMe",
            likeService.isLikedBy(BbsLikeTarget.ARTICLE, articleId, me));

        // 첨부파일 — 비밀글 본문이 차단된 경우는 첨부도 가리는 게 일관됨
        model.addAttribute("existingFiles",
            canSeeBody ? articleService.listAttachments(article.getFileGroupId())
                       : java.util.Collections.emptyList());

        model.addAttribute("fileGroupId",article.getFileGroupId());

        // 이전글/다음글 — 원본 게시판(article.bbsMasterId) 기준(통합 게시판 뷰에서도 원본 이웃 글로 이동)
        model.addAttribute("prevArticle", articleService.getPrev(article.getBbsMasterId(), article));
        model.addAttribute("nextArticle", articleService.getNext(article.getBbsMasterId(), article));


        // 댓글 — 본문이 보일 때만 노출 (비밀글 차단 시 댓글도 가린다)
        List<com.gonet.primary.board.comment.dto.BbsComment> comments =
            (canSeeBody && "Y".equalsIgnoreCase(master.getCommentYn()))
                ? commentService.listByArticle(articleId)
                : java.util.Collections.emptyList();
        model.addAttribute("comments", comments);
        // 댓글별 좋아요 상태 — Map<commentId, Boolean>
        java.util.Map<String, Boolean> commentLikedIds = new java.util.HashMap<>();
        for (com.gonet.primary.board.comment.dto.BbsComment c : comments) {
            commentLikedIds.put(c.getCommentId(),
                likeService.isLikedBy(BbsLikeTarget.COMMENT, c.getCommentId(), me));
        }
        model.addAttribute("commentLikedIds", commentLikedIds);
        if (!model.containsAttribute("commentForm")) {
            com.gonet.primary.board.comment.dto.BbsCommentSaveForm cf =
                new com.gonet.primary.board.comment.dto.BbsCommentSaveForm();
            cf.setArticleId(articleId);
            model.addAttribute("commentForm", cf);
        }
        return viewPath(master, "detail");
    }

    // ==================================================================
    // 작성 폼 / 등록
    // ==================================================================

    @GetMapping("/write")
    public String writeForm(@PathVariable String siteCode,
                              @PathVariable String bbsCode,
                              Model model) {
        BbsMaster master = resolveMaster(siteCode, bbsCode);
        ensureReadable(master);            // 읽기 권한 통과 후만 글쓰기 폼 진입
        articleService.ensureCanWrite(master, currentUser());

        if (!model.containsAttribute("form")) {
            BbsArticleSaveForm form = new BbsArticleSaveForm();
            form.setBbsMasterId(master.getBbsMasterId());
            // 첨부 picker 가 entityId 로 사용할 articleId 를 사전 발급.
            // POST 시 Service 가 동일 ID 를 그대로 받아 INSERT — 그룹 lookup 일치 보장.
            form.setArticleId(UuidV7Generator.generate("ART"));
            model.addAttribute("form", form);
        }
        model.addAttribute("master", master);
        model.addAttribute("siteId",   master.getSiteId());
        model.addAttribute("siteCode", siteCode);
        model.addAttribute("bbsCode", bbsCode);
        model.addAttribute("menuId",   master.getMenuId());
        model.addAttribute("mode", "write");
        // 신규 작성 — picker 의 existingFiles 는 빈 list. 사용자가 picker 로 업로드하면 AJAX 로
        // 서버에 파일이 생성되고 hidden input 에 fileId 가 추가됨.
        model.addAttribute("existingFiles", java.util.Collections.emptyList());

        model.addAttribute("fileGroupId",UuidV7Generator.generate("FG0"));

        model.addAttribute("categories",
            categoryService.listActive(master.getBbsMasterId()));
        model.addAttribute("canEditWriterName", isStaffOrAbove(currentUser()));
        return viewPath(master, "write");
    }

    @PostMapping
    public String submit(@PathVariable String siteCode,
                          @PathVariable String bbsCode,
                          @Valid @ModelAttribute("form") BbsArticleSaveForm form,
                          BindingResult br,
                          HttpServletRequest req,
                          RedirectAttributes ra,
                          HttpServletResponse res) {
        BbsMaster master = resolveMaster(siteCode, bbsCode);
        ensureReadable(master);
        // ensureCanWrite 는 Service.create() 가 호출 — submit 단계에서 한 번 더 호출하지 않는다
        form.setBbsMasterId(master.getBbsMasterId());

        if (br.hasErrors()) {
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("errors", br.getAllErrors());
            return "redirect:/bbs/" + siteCode + "/" + bbsCode + "/write";
        }
        // CAPTCHA — 게시판 마스터에서 토글된 경우만 (master.captchaYn='Y')
        if ("Y".equalsIgnoreCase(master.getCaptchaYn()) && !captchaGuard.verifyOrFail(req)) {
            log.warn("BBS_WRITE captcha_fail siteCode={} bbsCode={} ip={}",
                siteCode, bbsCode, IpUtils.clientIp(req));
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", "보안 검증에 실패했습니다. 다시 시도해 주세요.");
            return "redirect:/bbs/" + siteCode + "/" + bbsCode + "/write";
        }
        try {
            String id = articleService.create(form, IpUtils.clientIp(req));
            String target = "/bbs/" + siteCode + "/" + bbsCode + "/" + id;
            res.setHeader("HX-Redirect", target);
            ra.addFlashAttribute("message", "글을 등록했습니다.");
            return "redirect:" + target;
        } catch (AccessDeniedException ex) {
            log.info("===BBS_USR_WRITE denied site={} bbs={} reason={}",
                siteCode, bbsCode, ex.getMessage());
            throw ex;
        } catch (IllegalArgumentException | IllegalStateException ex) {
            log.warn("BBS_USR_WRITE fail site={} bbs={} reason={}",
                siteCode, bbsCode, ex.getMessage());
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/bbs/" + siteCode + "/" + bbsCode + "/write";
        }
    }

    // ==================================================================
    // 수정 폼 / 수정
    // ==================================================================

    @GetMapping("/{articleId}/edit")
    public String editForm(@PathVariable String siteCode,
                             @PathVariable String bbsCode,
                             @PathVariable String articleId,
                             Model model,
                             RedirectAttributes ra) {
        BbsMaster master = resolveMaster(siteCode, bbsCode);
        ensureReadable(master);

        BbsArticle article = articleService.get(master.getBbsMasterId(), articleId);
        if (article == null) {
            ra.addFlashAttribute("error", "게시글을 찾을 수 없습니다.");
            return "redirect:/bbs/" + siteCode + "/" + bbsCode;
        }
        CustomUserDetails me = currentUser();
        log.info("===BBS_USR_EDIT_FORM_ENTER articleId={} site={} bbs={} userId={} userType={}",
            articleId, siteCode, bbsCode,
            me != null ? me.getUserId() : "null",
            me != null ? me.getUserType() : "null");
        if (!canEdit(article)) {
            log.warn("BBS_USR_EDIT_FORM_DENIED articleId={} userId={} userType={}",
                articleId,
                me != null ? me.getUserId() : "null",
                me != null ? me.getUserType() : "null");
            throw new AccessDeniedException("작성자 본인 또는 관리자만 수정할 수 있습니다.");
        }
        model.addAttribute("form", toForm(article));
        model.addAttribute("master", master);
        model.addAttribute("article", article);
        model.addAttribute("siteId",   master.getSiteId());
        model.addAttribute("siteCode", siteCode);
        model.addAttribute("bbsCode", bbsCode);
        model.addAttribute("menuId",   master.getMenuId());
        model.addAttribute("mode", "edit");
        model.addAttribute("existingFiles", articleService.listAttachments(article.getFileGroupId()));
        model.addAttribute("fileGroupId",article.getFileGroupId());

        model.addAttribute("categories",
            categoryService.listActive(master.getBbsMasterId()));
        model.addAttribute("canEditWriterName", isStaffOrAbove(currentUser()));
        return viewPath(master, "write");
    }

    @PostMapping("/{articleId}")
    public String update(@PathVariable String siteCode,
                          @PathVariable String bbsCode,
                          @PathVariable String articleId,
                          @Valid @ModelAttribute("form") BbsArticleSaveForm form,
                          BindingResult br,
                          HttpServletRequest req,
                          RedirectAttributes ra,
                          HttpServletResponse res) {
        BbsMaster master = resolveMaster(siteCode, bbsCode);
        ensureReadable(master);
        form.setBbsMasterId(master.getBbsMasterId());
        form.setArticleId(articleId);

        if (br.hasErrors()) {
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("errors", br.getAllErrors());
            return "redirect:/bbs/" + siteCode + "/" + bbsCode + "/" + articleId + "/edit";
        }
        try {
            articleService.update(form, IpUtils.clientIp(req));
            log.info("===BBS_USR_UPDATE_OK id={} site={} bbs={}", articleId, siteCode, bbsCode);
            String target = "/bbs/" + siteCode + "/" + bbsCode + "/" + articleId;
            res.setHeader("HX-Redirect", target);
            ra.addFlashAttribute("message", "글을 수정했습니다.");
            return "redirect:" + target;
        } catch (AccessDeniedException ex) {
            log.warn("BBS_USR_UPDATE_DENIED id={} reason={}", articleId, ex.getMessage());
            throw ex;
        } catch (IllegalArgumentException ex) {
            log.warn("BBS_USR_UPDATE fail id={} reason={}", articleId, ex.getMessage());
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/bbs/" + siteCode + "/" + bbsCode + "/" + articleId + "/edit";
        }
    }

    // ==================================================================
    // 삭제
    // ==================================================================

    @PostMapping("/{articleId}/delete")
    public String delete(@PathVariable String siteCode,
                          @PathVariable String bbsCode,
                          @PathVariable String articleId,
                          RedirectAttributes ra,
                          HttpServletResponse res) {
        try {
            articleService.softDelete(articleId);
            log.info("===BBS_USR_DELETE_OK id={} site={} bbs={}", articleId, siteCode, bbsCode);
            String target = "/bbs/" + siteCode + "/" + bbsCode;
            res.setHeader("HX-Redirect", target);
            ra.addFlashAttribute("message", "글을 삭제했습니다.");
            return "redirect:" + target;
        } catch (AccessDeniedException ex) {
            log.warn("BBS_USR_DELETE_DENIED id={} reason={}", articleId, ex.getMessage());
            throw ex;
        } catch (IllegalArgumentException ex) {
            log.warn("BBS_USR_DELETE fail id={} reason={}", articleId, ex.getMessage());
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/bbs/" + siteCode + "/" + bbsCode + "/" + articleId;
        }
    }

    // ==================================================================
    // 헬퍼
    // ==================================================================

    /**
     * 게시판 타입별 뷰 경로 분기 — {@code front/board/{BBS_TYPE}/{view}}.
     *
     * <p>예: bbs_type=FREE 이면 {@code "front/board/FREE/list"}.
     *
     * <p>{@link BbsType#safeParse(String)} 로 파싱 — 알 수 없는 값은 {@code FREE} 폴더로 폴백.
     * 새 타입 추가 시 동일 이름의 폴더에 list/detail/write 3 파일을 만들어주면
     * 자동으로 분기된다 (분기 코드 수정 불필요).
     *
     * @param master 현재 게시판 마스터 (bbs_type 추출)
     * @param view   list / detail / write 중 하나
     * @return 템플릿 경로
     */
    private static String viewPath(BbsMaster master, String view) {
        BbsType type = BbsType.safeParse(master.getBbsType());
        return "front/board/" + type.name() + "/" + view;
    }

    private BbsMaster resolveMaster(String siteCode, String bbsCode) {
        BbsMaster m = masterService.getBySiteCodeAndBbsCode(siteCode, bbsCode);
        if (m == null) {
            log.warn("BBS_MASTER_NOT_FOUND site={} bbs={}", siteCode, bbsCode);
            throw new IllegalArgumentException("게시판을 찾을 수 없습니다: " + siteCode + "/" + bbsCode);
        }
        return m;
    }

    /**
     * 읽기 권한 검증 — useable 확인 + tb_bbs_master.read_auth 강제.
     * Service 단의 {@link BoardArticleService#ensureCanRead} 로 위임.
     */
    private void ensureReadable(BbsMaster m) {
        ensureUseable(m);
        articleService.ensureCanRead(m, currentUser());
    }

    private static void ensureUseable(BbsMaster m) {
        if (!"Y".equalsIgnoreCase(m.getUseYn())) {
            log.warn("BBS_BOARD_DISABLED bbsMasterId={} code={}", m.getBbsMasterId(), m.getBbsCode());
            throw new IllegalStateException("사용중지된 게시판입니다.");
        }
    }

    private static boolean canWrite(BbsMaster m) {
        CustomUserDetails me = currentUser();
        if (me == null) {
            log.info("===[BoardUsrController.canWrite] me=null");
            return false;
        }
        String wa = m.getWriteAuth() == null ? "MEMBER" : m.getWriteAuth().toUpperCase();
        String type = me.getUserType() == null ? "MEMBER" : me.getUserType().toUpperCase();
        boolean result = switch (wa) {
            case "ALL", "GUEST", "MEMBER" -> true;
            case "EMPLOYEE" -> type.equals("EMPLOYEE") || type.equals("STAFF") || type.equals("ADMIN");
            case "ADMIN"    -> type.equals("STAFF") || type.equals("ADMIN");
            default          -> false;
        };
        log.info("===[BoardUsrController.canWrite] writeAuth={}, userType={}, result={}", wa, type, result);
        return result;
    }

    /**
     * 수정 권한 — 본인 OR STAFF 이상.
     * STAFF 가 수정 가능한 이유: 작성자명(writer_name) 을 대신 수정해야 하는 운영 사례
     * (대필·게시 정정 등). 본인 판정: writerUserId 또는 createdBy 둘 중 하나라도 일치.
     */
    private static boolean canEdit(BbsArticle a) {
        CustomUserDetails me = currentUser();
        if (me == null) {
            log.info("===[BoardUsrController.canEdit] me=null");
            return false;
        }
        boolean staff = isStaffOrAbove(me);
        boolean owner = isOwner(a, me);
        log.info("===[BoardUsrController.canEdit] articleId={}, userId={}, userType={}, isStaffOrAbove={}, isOwner={}", 
            a.getArticleId(), me.getUserId(), me.getUserType(), staff, owner);
        if (staff) return true;
        return owner;
    }

    /**
     * 삭제 권한 — 본인 OR userType=ADMIN.
     * EMPLOYEE 는 ADMIN 이 아니므로 통과 못함.
     */
    private static boolean canDelete(BbsArticle a) {
        CustomUserDetails me = currentUser();
        if (me == null) {
            log.info("===[BoardUsrController.canDelete] me=null");
            return false;
        }
        boolean staff = "STAFF".equalsIgnoreCase(me.getUserType()) || "ADMIN".equalsIgnoreCase(me.getUserType());
        boolean owner = isOwner(a, me);
        log.info("===[BoardUsrController.canDelete] articleId={}, userId={}, userType={}, isSTAFF/ADMIN={}, isOwner={}", 
            a.getArticleId(), me.getUserId(), me.getUserType(), staff, owner);
        if (staff) return true;
        return owner;
    }

    /**
     * 비밀글 본문 조회 — 본인 OR userType=STAFF.
     * EMPLOYEE 는 본문 조회 불가 — 관리자(STAFF)만 들춰볼 수 있도록 STAFF 로 좁힘.
     */
    private static boolean canSeeSecret(BbsArticle a) {
        CustomUserDetails me = currentUser();
        if (me == null) {
            log.info("===[BoardUsrController.canSeeSecret] me=null");
            return false;
        }
        boolean staff = "STAFF".equalsIgnoreCase(me.getUserType()) || "ADMIN".equalsIgnoreCase(me.getUserType());
        boolean owner = isOwner(a, me);
        log.info("===[BoardUsrController.canSeeSecret] articleId={}, userId={}, userType={}, isSTAFF/ADMIN={}, isOwner={}", 
            a.getArticleId(), me.getUserId(), me.getUserType(), staff, owner);
        if (staff) return true;
        return owner;
    }

    /**
     * 본인 판정 — writerUserId 또는 createdBy 중 하나라도 일치하면 본인.
     */
    private static boolean isOwner(BbsArticle a, CustomUserDetails me) {
        if (a == null || me == null || me.getUserId() == null) return false;
        String mine = me.getUserId();
        return mine.equals(a.getWriterUserId()) || mine.equals(a.getCreatedBy());
    }

    private static CustomUserDetails currentUser() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !a.isAuthenticated()) return null;
        Object p = a.getPrincipal();
        return (p instanceof CustomUserDetails ud) ? ud : null;
    }

    /**
     * STAFF 이상 — 작성자명(writer_name) 입력·수정 등 모더레이션 게이트.
     *
     * <p>역할 계층 root → leaf:
     * {@code ROLE_SUPER → ROLE_ADMIN → ROLE_MANAGER → ROLE_STAFF → ROLE_EMPLOYEE}.
     * closure descendant expansion 으로 STAFF 이상 사용자는 모두 {@code ROLE_STAFF}
     * authority 를 보유. EMPLOYEE 는 STAFF 의 child 라 더 낮음 — 제외.
     */
    private static boolean isStaffOrAbove(CustomUserDetails me) {
        if (me == null) return false;
        boolean result = me.getAuthorities().stream()
            .anyMatch(a -> {
                String auth = a.getAuthority();
                return "ROLE_STAFF".equals(auth) || "ROLE_ADMIN".equals(auth) || "ADMIN".equals(auth);
            });
        log.info("===[BoardUsrController.isStaffOrAbove] userId={}, authorities={}, result={}", 
            me.getUserId(), me.getAuthorities(), result);
        return result;
    }

    private static boolean shouldCountView(HttpServletRequest req, String articleId) {
        Cookie[] cookies = req.getCookies();
        if (cookies == null) return true;
        String name = "bbs_v_" + sanitizeCookieName(articleId);
        for (Cookie c : cookies) {
            if (name.equals(c.getName())) return false;
        }
        return true;
    }

    private static void stampViewCookie(HttpServletResponse res, String articleId) {
        Cookie c = new Cookie("bbs_v_" + sanitizeCookieName(articleId), "1");
        c.setHttpOnly(true);
        c.setSecure(true);
        c.setPath("/");
        c.setMaxAge(VIEW_COUNT_COOKIE_TTL_SECONDS);
        c.setAttribute("SameSite", "Lax");
        res.addCookie(c);
    }

    /** UUID v7 은 하이픈을 포함 — 쿠키 이름엔 허용되지만 보수적으로 제거. */
    private static String sanitizeCookieName(String s) {
        return s == null ? "" : s.replace('-', '_');
    }

    private static BbsArticleSaveForm toForm(BbsArticle a) {
        BbsArticleSaveForm f = new BbsArticleSaveForm();
        f.setArticleId(a.getArticleId());
        f.setBbsMasterId(a.getBbsMasterId());
        f.setCategoryId(a.getCategoryId());
        f.setTitle(a.getTitle());
        f.setContent(a.getContent());
        f.setPressName(a.getPressName());
        f.setLinkUrl(a.getLinkUrl());
        f.setPublishedAt(a.getPublishedAt() == null ? null : a.getPublishedAt().toString());
        f.setWriterName(a.getWriterName());
        f.setSecretYn(a.getSecretYn());
        f.setNoticeYn(a.getNoticeYn());
        return f;
    }
}
