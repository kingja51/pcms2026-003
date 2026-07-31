package com.gonet.config.filter;

import com.gonet.config.security.SecurityProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 요청마다 CSP nonce 생성 후:
 * <ul>
 *   <li>HttpServletRequest 속성 "cspNonce" 로 Thymeleaf 에 전달</li>
 *   <li>Content-Security-Policy 헤더에 {@code 'nonce-XXX'} 주입</li>
 * </ul>
 *
 * <p>Thymeleaf 사용 예 (Spring MVC 가 request attribute 를 모델 변수로 노출):
 * <pre>
 *   &lt;script th:attr="nonce=${cspNonce}"&gt;...&lt;/script&gt;
 * </pre>
 * <p>주의: Thymeleaf 3.1+ 에서는 {@code #httpServletRequest} / {@code #request} 등
 * 서블릿 유틸리티 객체가 보안상 제거되어 사용 불가.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 40)
public class CspNonceFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(CspNonceFilter.class);

    public static final String NONCE_ATTR = "cspNonce";

    private static final SecureRandom RNG = new SecureRandom();

    private final SecurityProperties props;

    public CspNonceFilter(SecurityProperties props) {
        this.props = props;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain) throws ServletException, IOException {
        log.info("===CspNonceFilter executing for URI: {}", req.getRequestURI());
        String path = req.getRequestURI();
        // Swagger UI 는 inline script 를 사용하므로 CSP 적용 시 동작하지 않음 (백지 화면)
        if (!props.getCsp().isEnabled() || path.startsWith("/swagger-ui") || path.startsWith("/v3/api-docs")) {
            chain.doFilter(req, res);
            return;
        }

//        if (!props.getCsp().isEnabled()) {
//            chain.doFilter(req, res);
//            return;
//        }
        byte[] buf = new byte[18];
        RNG.nextBytes(buf);
        String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
        req.setAttribute(NONCE_ATTR, nonce);

        // nonce 를 품은 HTML 문서가 캐시되면 동일 nonce 가 여러 응답에 재사용되어
        // CSP nonce 의 전제(1요청 1난수)가 깨진다 → CSP 우회 위험. 최상위 문서(Accept: text/html)
        // 에만 no-store. 정적 자원(css/js/img)은 Accept 에 text/html 이 없어 불변 캐시 유지.
        String accept = req.getHeader("Accept");
        if (accept != null && accept.contains("text/html")) {
            res.setHeader("Cache-Control", "no-store");
        }

        // Reporting API 엔드포인트 그룹 선언 (report-to 가 참조)
        res.setHeader("Reporting-Endpoints", "gopcms-csp=\"/csp-report\"");
        res.setHeader("Content-Security-Policy", buildCsp(nonce, false));
        if (props.getCsp().isReportOnly()) {
            // 강제 정책에서 style-src 'unsafe-inline' 만 뺀 strict 변형 — 제거 가능 시점 측정용.
            res.setHeader("Content-Security-Policy-Report-Only", buildCsp(nonce, true));
        }
        chain.doFilter(req, res);
    }

    /**
     * @param nonce      요청별 1회용 nonce
     * @param reportOnly true 면 Content-Security-Policy-Report-Only 용(강제 아님) 변형.
     *                   enforce 정책과 동일하되 {@code style-src} 의 {@code 'unsafe-inline'} 을
     *                   제거하여 "인라인 스타일을 막으면 무엇이 깨지는가" 만 핀포인트로 수집한다
     *                   (3rd-party 위젯의 인라인 &lt;style&gt; 의존 제거 시점 판단용).
     */
    /**
     * 건양대 계열 신뢰 도메인 — 대학(konyang)·학사시스템(kyu)·병원(kyuh).
     * apex + 와일드카드 병기(*.는 apex 를 매칭하지 않음). 교수 사진(tis.kyu.ac.kr) 등
     * 교내 원본 콘텐츠 소비용으로 img/connect/frame-src 에 허용.
     * script-src 에는 넣지 않는다 — 'strict-dynamic' 하에서 host 목록은 무시되며(CSP3),
     * 외부 스크립트는 nonce 부트스트랩 경유가 원칙.
     */
    private static final String UNIV_HOSTS =
        "https://konyang.ac.kr https://*.konyang.ac.kr "
        + "https://kyu.ac.kr https://*.kyu.ac.kr "
        + "https://kyuh.ac.kr https://*.kyuh.ac.kr";

    private String buildCsp(String nonce, boolean reportOnly) {
        // style-src: Tailwind 는 v4 CLI 로 컴파일된 외부 CSS(/css/output.css)를 link 로 로드 —
        //   구 CDN(@tailwindcss/browser) 런타임 <style> 주입은 제거됨. 다만 Toast UI Editor 및
        //   Kakao·Naver·Google Map SDK 가 여전히 런타임에 인라인 <style> 을 주입하므로 enforce 는
        //   아직 'unsafe-inline' 유지. Report-Only(strict 변형)로 위반을 수집해, 그 의존이 사라지면
        //   'unsafe-inline' 제거 예정. (style 은 스크립트 실행 벡터가 아니며 XSS 1차 방어는 script-src 담당)
        // script-src: 'nonce-...' + 'strict-dynamic' — nonce 스크립트가 만든 2차 스크립트만 신뢰 전파.
        //   ⚠️ 'strict-dynamic' 이 있으면 CSP3 브라우저는 아래 host 화이트리스트를 '무시'한다(CSP2 폴백 전용).
        //   지도 SDK 는 nonce 부트스트랩이 동적 주입하므로 strict-dynamic 으로 동작.
        // connect-src: 로컬 htmx.min.js 의 //# sourceMappingURL 이 cdn.jsdelivr.net/sm/*.map 을 가리켜
        //   devtools 열림 시 fetch — 저위험 허용.
        // img-src: YOUTUBE 게시판이 i.ytimg.com HD 썸네일 직접 로드.
        // frame-src: YouTube 임베드 + reCAPTCHA invisible challenge(www.google.com).
        StringBuilder sb = new StringBuilder()
            .append("default-src 'self'; ")
            .append("base-uri 'self'; ")
            // 관리자 미리보기 same-origin iframe(`/preview-live`) 임베드 허용. cross-origin clickjacking 은 차단.
            .append("frame-ancestors 'self'; ")
            // NICE CheckPlus 안심본인인증 — /member/identity/nice 폼이 외부 도메인으로 POST
            .append("form-action 'self' https://nice.checkplus.co.kr; ")
            .append("object-src 'none'; ")
            // img-src: Kakao/Naver/Google Map 타일·마커·스트리트뷰, YouTube 썸네일,
            //   건양대 계열(UNIV_HOSTS) = 교수 사진(tis.kyu.ac.kr) 등 교내 원본 이미지
            .append("img-src 'self' data: blob: https://i.ytimg.com https://img.youtube.com ")
            .append("https://*.daumcdn.net https://dapi.kakao.com ")
            .append("https://*.map.naver.net https://*.pstatic.net https://*.naver.com ")
            .append("https://*.googleapis.com https://*.gstatic.com https://*.ggpht.com ")
            .append("https://*.google.com ").append(UNIV_HOSTS).append("; ")
            // font-src: Google Maps 의 Roboto (GFonts)
            .append("font-src 'self' data: https://fonts.gstatic.com; ");

        // ── style-src: enforce 는 'unsafe-inline' 유지, Report-Only 는 제거(=핀포인트 수집) ──
        //   fonts.googleapis.com: Google Maps CSS, uicdn.toast.com: Toast UI Editor CSS
        sb.append("style-src 'self' ");
        if (!reportOnly) {
            sb.append("'unsafe-inline' ");
        }
        sb.append("https://fonts.googleapis.com https://uicdn.toast.com; ");

        // script-src: 'wasm-unsafe-eval' 은 @rhwp/core HWP 뷰어의 WASM compile/instantiate 용
        //   (eval 과 무관, WASM 샌드박스 한정). host 목록은 strict-dynamic 하 CSP2 폴백 전용.
        sb.append("script-src 'self' 'nonce-").append(nonce).append("' 'strict-dynamic' 'wasm-unsafe-eval' ")
            .append("https://dapi.kakao.com https://t1.daumcdn.net ")
            .append("https://oapi.map.naver.com ")
            .append("https://maps.googleapis.com https://maps.gstatic.com; ")
            // connect-src: jsdelivr(소스맵/WASM/docx·SheetJS), esm.sh(pptx-preview), 지도 타일/검색 fetch,
            //   *.navercorp.com = Naver Map Nelo 텔레메트리(차단돼도 무해, 콘솔 경고 회피용),
            //   건양대 계열(UNIV_HOSTS) = 교내 시스템 fetch/XHR
            .append("connect-src 'self' https://cdn.jsdelivr.net https://esm.sh ")
            .append("https://dapi.kakao.com https://*.daumcdn.net ")
            .append("https://oapi.map.naver.com https://*.map.naver.net https://*.pstatic.net ")
            .append("https://*.naver.com https://*.navercorp.com ")
            .append("https://*.googleapis.com https://*.gstatic.com ").append(UNIV_HOSTS).append("; ")
            // frame-src: YouTube 임베드 + reCAPTCHA(api2/anchor·bframe iframe) + 건양대 계열 임베드
            .append("frame-src 'self' https://www.youtube-nocookie.com https://www.youtube.com ")
            .append("https://www.google.com ").append(UNIV_HOSTS).append("; ");

        // 위반 리포트 수집 — report-to(Reporting API) + report-uri(레거시 호환). 양 정책 공통.
        sb.append("report-to gopcms-csp; report-uri /csp-report");

        // upgrade-insecure-requests 는 Report-Only 에서 무효 + 콘솔 경고 유발 → enforce 에만.
        if (!reportOnly) {
            sb.append("; upgrade-insecure-requests");
        }
        return sb.toString();
    }
}
