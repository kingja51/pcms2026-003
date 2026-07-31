package com.gonet.primary.survey.controller;

import com.gonet.common.dto.PageResponse;
import com.gonet.common.util.IpUtils;
import com.gonet.primary.survey.dto.Survey;
import com.gonet.primary.survey.dto.SurveyMaster;
import com.gonet.primary.survey.dto.SurveySearch;
import com.gonet.primary.survey.service.SurveyMasterService;
import com.gonet.primary.survey.service.SurveyResponseService;
import com.gonet.primary.survey.service.SurveyService;
import com.gonet.primary.system.login.dto.CustomUserDetails;
import com.gonet.primary.system.site.dto.SiteContext;
import com.gonet.primary.system.site.service.SiteContextResolver;
import com.gonet.primary.system.site.service.SiteContextService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 사용자 측 설문 — {@code /survey/{siteCode}} 스코프.
 *
 * <p>siteCode 로 사이트를 해석하고 해당 사이트의 PUBLISHED + CLOSED 설문을 모두 노출.
 * 화면에서는 (a) 기간 만료, (b) status=CLOSED 인 경우 "설문 종료" 로 표기.
 */
@Controller
@RequestMapping("/survey/{siteCode}")
public class SurveyUsrController {

    private static final Logger log = LoggerFactory.getLogger(SurveyUsrController.class);

    private final SurveyService         service;
    private final SurveyResponseService responseService;
    private final SurveyMasterService masterService;
    private final SiteContextResolver siteContextResolver;
    private final SiteContextService siteContextService;

    public SurveyUsrController(SurveyService service,
                               SurveyResponseService responseService, SurveyMasterService masterService, SiteContextResolver siteContextResolver, SiteContextService siteContextService) {
        this.service            = service;
        this.responseService    = responseService;
        this.masterService = masterService;
        this.siteContextResolver = siteContextResolver;
        this.siteContextService = siteContextService;
    }

    @ModelAttribute
    public void injectSiteContext(@PathVariable(required = false) String siteCode,
                                  HttpServletRequest req, Model model) {
        if (siteCode == null || siteCode.isBlank()) return;

        // master 가 있으면 부가 정보(menuId/siteId/master) 추가 주입.
        SurveyMaster master = masterService.findBySiteCode(siteCode);

        if (master == null) {
            siteContextResolver.injectByCodeAndStick(siteCode, req, model);
            return;
        }

        model.addAttribute("master", master);
        model.addAttribute("surveyMasterId", master.getSurveyMasterId());
        model.addAttribute("menuId", master.getMenuId());
        model.addAttribute("siteId", master.getSiteId());
        // 사이트 컨텍스트 주입 + session sticky 동시 저장.
        //   master 가 아직 없는 사이트도 siteCode 만으로 layoutPath 가 채워지고,
        //   이후 /member/login 등 평면 URL 도메인도 동일 사이트 레이아웃을 유지.
        siteContextResolver.injectByCodeAndStick(master.getSiteCode(), req, model);
    }


    @GetMapping
    public String list(@PathVariable String siteCode,
                        @ModelAttribute("search") SurveySearch search,
                        Model model) {
        String siteId = resolveSiteId(siteCode);
        if (siteId == null) {
            model.addAttribute("page", PageResponse.of(List.of(), 1, 20, 0));
            model.addAttribute("rowsAnnotated", List.of());
            model.addAttribute("siteCode", siteCode);
            return "front/survey/list";
        }
        // 같은 사이트의 PUBLISHED + CLOSED 모두 노출, 사용중지(use_yn='N') 와 DRAFT 는 제외
        search.setSiteId(siteId);
        search.setUseYn("Y");
        // status 검색 파라미터는 무시 — 사용자 화면은 PUBLISHED + CLOSED 합집합 고정
        search.setStatus(null);

        List<Survey> all = service.search(search);
        // status='DRAFT' 는 안전하게 제외 (mapper 에서 useYn='Y' 가 사실상 제외하지만 방어)
        List<Survey> rows = new ArrayList<>(all.size());
        for (Survey sv : all) {
            if ("PUBLISHED".equals(sv.getStatus()) || "CLOSED".equals(sv.getStatus())) {
                rows.add(sv);
            }
        }
        int total = service.count(search);

        // 표시 상태 사전 계산 — 화면에서 SpEL 분기 대신 annotated map 사용
        LocalDateTime now = LocalDateTime.now();
        List<Map<String, Object>> annotated = new ArrayList<>(rows.size());
        for (Survey sv : rows) {
            boolean closed = "CLOSED".equals(sv.getStatus())
                || (sv.getEndAt() != null && now.isAfter(sv.getEndAt()));
            Map<String, Object> row = new HashMap<>();
            row.put("survey", sv);
            row.put("closed", closed);
            row.put("displayLabel", closed ? "설문 종료" : "진행중");
            annotated.add(row);
        }

        model.addAttribute("page",
            PageResponse.of(rows, search.getPage(), search.getPageSize(), total));
        model.addAttribute("rowsAnnotated", annotated);
        model.addAttribute("siteId",   siteId);
        model.addAttribute("siteCode", siteCode);
        return "front/survey/list";
    }

    @GetMapping("/{surveyId}")
    public String detail(@PathVariable String siteCode,
                          @PathVariable String surveyId,
                          Model model) {
        Survey sv = service.getWithDetails(surveyId);
        String siteId = resolveSiteId(siteCode);
        if (sv == null || !"Y".equals(sv.getUseYn())
            || siteId == null || !siteId.equals(sv.getSiteId())) {
            model.addAttribute("error", "설문을 찾을 수 없거나 사용중지 상태입니다.");
            model.addAttribute("siteCode", siteCode);
            return "front/survey/detail";
        }
        boolean closed = "CLOSED".equals(sv.getStatus())
            || (sv.getEndAt() != null && LocalDateTime.now().isAfter(sv.getEndAt()));

        CustomUserDetails me = currentUser();
        boolean isMember = me != null && me.getUserId() != null;
        boolean alreadyResponded = false;
        if (isMember && "Y".equalsIgnoreCase(sv.getOneResponseYn())) {
            alreadyResponded = responseService.hasResponded(surveyId, me.getUserId());
        }
        model.addAttribute("survey", sv);
        model.addAttribute("openNow", sv.isOpenNow() && !closed);
        model.addAttribute("closed", closed);
        model.addAttribute("isMember", isMember);
        model.addAttribute("alreadyResponded", alreadyResponded);
        model.addAttribute("siteId",   sv.getSiteId());
        model.addAttribute("siteCode", siteCode);
        model.addAttribute("menuId",   sv.getMenuId());
        return "front/survey/detail";
    }

    @PostMapping("/{surveyId}/submit")
    public String submit(@PathVariable String siteCode,
                          @PathVariable String surveyId,
                          HttpServletRequest req,
                          RedirectAttributes ra) {
        CustomUserDetails me = currentUser();
        if (me == null) throw new AccessDeniedException("로그인이 필요합니다.");

        Map<String, Object> answers = new HashMap<>();
        Map<String, String[]> params = req.getParameterMap();
        for (Map.Entry<String, String[]> e : params.entrySet()) {
            String name = e.getKey();
            if (!name.startsWith("q_")) continue;
            String qid = name.substring(2);
            String[] vals = e.getValue();
            if (vals == null || vals.length == 0) continue;
            if (vals.length == 1) answers.put(qid, vals[0]);
            else                  answers.put(qid, java.util.Arrays.asList(vals));
        }

        try {
            String responseId = responseService.submit(surveyId, answers, me, IpUtils.clientIp(req));
            ra.addFlashAttribute("message", "응답이 제출되었습니다. 감사합니다.");
            log.info("===SURVEY_SUBMIT_OK survey={} response={} member={}",
                surveyId, responseId, me.getUserId());
            return "redirect:/survey/" + siteCode + "/" + surveyId + "/thanks";
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/survey/" + siteCode + "/" + surveyId;
        } catch (Exception ex) {
            log.warn("SURVEY_SUBMIT error", ex);
            ra.addFlashAttribute("error", "응답 제출 중 오류가 발생했습니다.");
            return "redirect:/survey/" + siteCode + "/" + surveyId;
        }
    }

    @GetMapping("/{surveyId}/thanks")
    public String thanks(@PathVariable String siteCode,
                          @PathVariable String surveyId,
                          Model model) {
        Survey sv = service.get(surveyId);
        model.addAttribute("survey", sv);
        model.addAttribute("siteCode", siteCode);
        return "front/survey/thanks";
    }

    private String resolveSiteId(String siteCode) {
        if (siteCode == null || siteCode.isBlank()) return null;
        SiteContext ctx = siteContextService.getContextByCode(siteCode);
        if (ctx == null || ctx.getSite() == null) return null;
        return ctx.getSite().getSiteId();
    }

    private static CustomUserDetails currentUser() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !a.isAuthenticated()) return null;
        Object p = a.getPrincipal();
        return (p instanceof CustomUserDetails ud) ? ud : null;
    }
}
