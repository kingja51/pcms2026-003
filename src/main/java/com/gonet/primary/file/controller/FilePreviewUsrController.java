package com.gonet.primary.file.controller;

import com.gonet.common.file.service.DocumentConvertService;
import com.gonet.primary.file.dto.FileItem;
import com.gonet.primary.file.service.FileService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.Optional;

/**
 * 사용자 측 파일 뷰어 — {@code /file/preview/{fileId}}.
 *
 * <p>지원 정책:
 * <ul>
 *   <li>{@code .pdf} — 브라우저 네이티브 PDF 뷰어 (iframe)</li>
 *   <li>{@code .txt} — iframe 으로 인플레이스 텍스트 표시</li>
 *   <li>{@code .hwp / .hwpx} — rhwp WASM 클라이언트 렌더 (Canvas)</li>
 *   <li>{@code .doc / .docx} — LibreOffice 서버 변환 → PDF.js 뷰어 (DocumentConvertService 활성 시).
 *       비활성 시: docx 는 docx-preview ESM 클라이언트 폴백, doc 는 미지원 페이지로 폴백.</li>
 *   <li>{@code .xlsx} — SheetJS ESM 클라이언트 + 시트 탭 + HTML 테이블</li>
 *   <li>{@code .pptx} — LibreOffice 서버 변환 → PDF.js 뷰어 (DocumentConvertService 활성 시).
 *       비활성 시 미지원 페이지로 폴백.</li>
 *   <li>그 외 — 미지원 페이지 + 다운로드 안내</li>
 * </ul>
 *
 * <p>권한: {@link FileService#previewInline} 가 {@code tb_file_group.download_auth} 정책을
 * 다운로드와 동일하게 평가 — 미인증 시 로그인 페이지 redirect, 권한 부족 시 403.
 *
 * <p>두 엔드포인트:
 * <ul>
 *   <li>{@code GET /file/preview/{fileId}} — 뷰어 HTML 페이지 (레이아웃 포함)</li>
 *   <li>{@code GET /file/preview/{fileId}/inline} — 파일 바이너리, {@code Content-Disposition: inline}
 *       (HTML 페이지의 iframe src 가 가리킴)</li>
 * </ul>
 */
@Controller
@RequestMapping("/file/preview")
public class FilePreviewUsrController {

    private final FileService service;
    /** LibreOffice 변환 서비스 — gopcms.file.converter.enabled=true 시에만 빈 등록 → Optional. */
    private final Optional<DocumentConvertService> converter;

    public FilePreviewUsrController(FileService service,
                                    @Autowired(required = false) DocumentConvertService converter) {
        this.service = service;
        this.converter = Optional.ofNullable(converter);
    }

    /** 뷰어 HTML 페이지 — 확장자별로 분기 렌더. */
    @GetMapping("/{fileId}")
    public String previewPage(@PathVariable String fileId, Model model) {
        FileItem f = service.get(fileId);
        if (f == null || "Y".equals(f.getDeleteYn())) {
            return "redirect:/error/404";
        }
        // 권한 검증은 inline 엔드포인트에서 한 번 더 수행 — viewer 페이지 자체는 권한 누설
        // 위험 없는 메타데이터(파일명·확장자)만 노출.

        String ext = f.getExtension() == null ? "" : f.getExtension().toLowerCase();

        model.addAttribute("file", f);
        model.addAttribute("fileId", fileId);
        model.addAttribute("originalName", f.getOriginalName());
        model.addAttribute("extension", ext);
        model.addAttribute("inlineUrl", "/file/preview/" + fileId + "/inline");
        model.addAttribute("downloadUrl", "/fileDown/" + fileId);

        return switch (ext) {
            case "pdf"        -> "front/file/preview-pdf";
            case "txt"        -> "front/file/preview-txt";
            case "hwp", "hwpx"
                              -> "front/file/preview-hwp";           // rhwp WASM 클라이언트 렌더
            // doc/docx — LibreOffice 서버 변환 우선.
            // 컨버터 비활성 시: docx 는 docx-preview 클라이언트(backup) 폴백, doc 는 미지원.
            case "docx"       -> converter.isPresent()
                              ? "front/file/preview-pdf"             // LibreOffice → PDF → PDF.js 뷰어
                              : "front/file/preview-docx";           // backup: docx-preview ESM 클라이언트
            case "doc"        -> converter.isPresent()
                              ? "front/file/preview-pdf"             // LibreOffice → PDF → PDF.js 뷰어
                              : "front/file/preview-unsupported";   // 컨버터 비활성 시 다운로드 안내
            case "xlsx"       -> "front/file/preview-xlsx";          // SheetJS 클라이언트
            case "pptx"       -> converter.isPresent()
                              ? "front/file/preview-pdf"             // LibreOffice → PDF → PDF.js 뷰어
                              : "front/file/preview-unsupported";   // 컨버터 비활성 시 다운로드 안내
            default           -> "front/file/preview-unsupported";
        };
    }

    /**
     * 파일 바이너리 — Content-Disposition: inline. iframe src 또는 직접 fetch 용.
     * GET/HEAD 모두 지원 (HEAD 는 헤더만).
     */
    @RequestMapping(value = "/{fileId}/inline", method = {RequestMethod.GET, RequestMethod.HEAD})
    public void inline(@PathVariable String fileId,
                       HttpServletRequest req, HttpServletResponse res) {
        service.previewInline(fileId, req, res);
    }
}
