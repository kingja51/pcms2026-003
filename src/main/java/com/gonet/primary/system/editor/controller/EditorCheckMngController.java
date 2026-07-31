package com.gonet.primary.system.editor.controller;

import com.gonet.common.html.HtmlSanitizer;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 위지윅 에디터 확인 화면 (P3 DoD).
 *
 * <p>편집 결과가 원본 {@code textarea} 로 동기화돼 <b>서버까지 도달하는지</b> 눈으로 확인한다.
 * 제출값을 그대로 되돌려 보여주는 것이 전부이므로 Service 계층을 두지 않는다 —
 * 저장하는 것이 없어 비즈니스 로직이 없다(호환성 규칙 3 은 "비즈니스 로직"을 대상으로 한다).
 *
 * <p><b>에디터 산출 HTML 은 신뢰 입력이 아니다.</b> 되돌려 보여주기 전에
 * {@link HtmlSanitizer} 로 정화한다 — 이 화면 자체가 저장형 XSS 통로가 되면 안 된다.
 * 실제 도메인에서도 저장 경로에서 동일하게 정화한다(개발가이드 §10).
 *
 * <p>접근 규칙은 {@code tb_role_url_access} 에 등록해야 한다 — 무매칭 DENY 다(상시 게이트 3).
 */
@Controller
@RequestMapping("/admin/system/editor-check")
public class EditorCheckMngController {

    private static final String VIEW = "admin/system/editor-check";

    private final HtmlSanitizer sanitizer;

    public EditorCheckMngController(HtmlSanitizer sanitizer) {
        this.sanitizer = sanitizer;
    }

    @GetMapping
    public String form() {
        return VIEW;
    }

    @PostMapping
    public String submit(@RequestParam(required = false) String bodyTiptap,
                         @RequestParam(required = false) String bodyDefault,
                         @RequestParam(required = false) String bodyNamo,
                         Model model) {

        Map<String, String> received = new LinkedHashMap<>();
        received.put("bodyTiptap  (화면 지정)",   clean(bodyTiptap));
        received.put("bodyDefault (전역 기본)",   clean(bodyDefault));
        received.put("bodyNamo    (평문 폴백)",   clean(bodyNamo));

        model.addAttribute("submitted", received);
        // 폼에 값을 되돌려 채운다 — 정화된 값이다.
        model.addAttribute("bodyTiptap",  received.get("bodyTiptap  (화면 지정)"));
        model.addAttribute("bodyDefault", received.get("bodyDefault (전역 기본)"));
        model.addAttribute("bodyNamo",    received.get("bodyNamo    (평문 폴백)"));
        return VIEW;
    }

    private String clean(String raw) {
        if (raw == null || raw.isBlank()) return "";
        return sanitizer.sanitizeContent(raw);
    }
}
