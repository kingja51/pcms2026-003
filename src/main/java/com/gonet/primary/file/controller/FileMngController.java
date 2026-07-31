package com.gonet.primary.file.controller;

import com.gonet.common.dto.PageResponse;
import com.gonet.common.util.UuidV7Generator;
import com.gonet.primary.file.dto.FileEntityType;
import com.gonet.primary.file.dto.FileItem;
import com.gonet.primary.file.dto.FileSearch;
import com.gonet.primary.file.dto.UploadRequest;
import com.gonet.primary.file.dto.UploadResult;
import com.gonet.primary.file.dto.VirusScanStatus;
import com.gonet.common.file.security.UnauthenticatedUploadException;
import com.gonet.common.file.security.UploadValidationException;
import com.gonet.primary.file.service.FileService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * 파일 관리 화면 (관리자) — {@code /admin/system/file}.
 *
 * <p>업로드는 REST API ({@link FileApiController}) 전담. 본 Controller 는 목록·상세·soft delete 만.
 */
@Controller
@RequestMapping("/admin/system/file")
public class FileMngController {

    private static final Logger log = LoggerFactory.getLogger(FileMngController.class);

    private final FileService service;

    public FileMngController(FileService service) {
        this.service = service;
    }

    @GetMapping
    public String list(@ModelAttribute("search") FileSearch search, Model model) {
        List<FileItem> rows = service.search(search);
        int total = service.count(search);
        model.addAttribute("page",
            PageResponse.of(rows, search.getPage(), search.getPageSize(), total));
        model.addAttribute("scanStatuses", VirusScanStatus.values());
        return "admin/system/file/list";
    }

    // ==================================================================
    // 업로드 폼 — 관리자 수동 업로드 (REST /api/v1/file/upload 와 동일 Service 경유)
    // ==================================================================

    @GetMapping("/new")
    public String uploadForm(Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new UploadRequest());
        }
        model.addAttribute("entityTypes", FileEntityType.values());
        // 첨부파일 그룹 ID (미리생성)
        model.addAttribute("fileGroupId",UuidV7Generator.generate("FG0"));
        return "admin/system/file/form";
    }

    @PostMapping
    public String upload(@Valid @ModelAttribute("form") UploadRequest form,
                          BindingResult br,
                          @RequestParam(value = "file", required = false) MultipartFile file,
                          RedirectAttributes ra,
                          HttpServletResponse res) {
        if (file == null || file.isEmpty()) {
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", "업로드할 파일을 선택하세요.");
            return "redirect:/admin/system/file/new";
        }
        if (br.hasErrors()) {
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("errors", br.getAllErrors());
            return "redirect:/admin/system/file/new";
        }
        try {
            // 관리자 수동 업로드는 카테고리 무관 — 확장자/Tika 는 allowed 전체 기준.
            // directory 는 entityType 를 그대로 사용 (FileServiceImpl.resolveDirectory 가 fallback).
            String fileGroupId = UuidV7Generator.generate("FG0");
            UploadResult result = service.upload(form, file, form.getEntityType(), fileGroupId);
            log.info("===FILE_UPLOAD ok fileId={} original={} size={}",
                result.getFileId(), result.getOriginalName(), result.getSizeBytes());
            ra.addFlashAttribute("message",
                "파일을 등록했습니다. 바이러스 검사 대기 중입니다.");
            String target = "/admin/system/file/" + result.getFileId();
            res.setHeader("HX-Redirect", target);
            return "redirect:" + target;
        } catch (UnauthenticatedUploadException ex) {
            // 이중 방어 — /admin/** 체인이 이미 인증 요구하지만 common 엔진이 재확인
            log.info("===FILE_UPLOAD denied-unauth original={}", file.getOriginalFilename());
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/login";
        } catch (UploadValidationException ex) {
            log.info("===FILE_UPLOAD rejected original={} reason={}",
                file.getOriginalFilename(), ex.getMessage());
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/system/file/new";
        } catch (IllegalArgumentException ex) {
            log.warn("FILE_UPLOAD fail original={} reason={}",
                file.getOriginalFilename(), ex.getMessage());
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/system/file/new";
        } catch (Exception ex) {
            log.warn("FILE_UPLOAD error original={} reason={}",
                file.getOriginalFilename(), ex.getMessage(), ex);
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", "업로드 처리 중 오류가 발생했습니다.");
            return "redirect:/admin/system/file/new";
        }
    }

    // ==================================================================
    // 다중 업로드 (bulk) — 여러 파일을 한 번에 등록. 부분 성공 허용.
    // ==================================================================

    @GetMapping("/new/bulk")
    public String bulkForm(Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new UploadRequest());
        }
        model.addAttribute("entityTypes", FileEntityType.values());
        return "admin/system/file/form-bulk";
    }

    @PostMapping("/bulk")
    public String bulkUpload(@Valid @ModelAttribute("form") UploadRequest form,
                              BindingResult br,
                              @RequestParam(value = "files", required = false) MultipartFile[] files,
                              RedirectAttributes ra,
                              HttpServletResponse res) {
        if (files == null || files.length == 0) {
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", "업로드할 파일을 하나 이상 선택하세요.");
            return "redirect:/admin/system/file/new/bulk";
        }
        if (br.hasErrors()) {
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("errors", br.getAllErrors());
            return "redirect:/admin/system/file/new/bulk";
        }

        int ok = 0;
        List<String> failures = new java.util.ArrayList<>();
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) continue;
            try {
                String fileGroupId = UuidV7Generator.generate("FG0");
                UploadResult result = service.upload(form, file, form.getEntityType(),fileGroupId);
                log.info("===FILE_UPLOAD_BULK_OK fileId={} original={} size={}",
                    result.getFileId(), result.getOriginalName(), result.getSizeBytes());
                ok++;
            } catch (UnauthenticatedUploadException ex) {
                // 비인증은 즉시 전체 중단 + 로그인 페이지로
                log.info("===FILE_UPLOAD_BULK denied-unauth");
                ra.addFlashAttribute("error", ex.getMessage());
                return "redirect:/admin/login";
            } catch (UploadValidationException | IllegalArgumentException ex) {
                log.info("===FILE_UPLOAD_BULK rejected original={} reason={}",
                    file.getOriginalFilename(), ex.getMessage());
                failures.add(file.getOriginalFilename() + " — " + ex.getMessage());
            } catch (Exception ex) {
                log.warn("FILE_UPLOAD_BULK error original={} reason={}",
                    file.getOriginalFilename(), ex.getMessage(), ex);
                failures.add(file.getOriginalFilename() + " — 처리 중 오류");
            }
        }

        if (ok == 0 && failures.isEmpty()) {
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("error", "업로드할 파일이 없습니다. (모두 비어 있음)");
            return "redirect:/admin/system/file/new/bulk";
        }
        if (ok > 0) {
            ra.addFlashAttribute("message",
                "총 " + ok + " 건을 등록했습니다. 바이러스 검사 대기 중입니다."
                + (failures.isEmpty() ? "" : " (실패 " + failures.size() + " 건)"));
        }
        if (!failures.isEmpty()) {
            ra.addFlashAttribute("error",
                "다음 " + failures.size() + " 건은 실패했습니다:\n" + String.join("\n", failures));
        }

        String target = "/admin/system/file";
        res.setHeader("HX-Redirect", target);
        return "redirect:" + target;
    }

    // ==================================================================
    // File Picker 모듈 데모/테스트 페이지
    //   · /admin/system/file/demo           — 모드 선택 허브
    //   · /admin/system/file/demo/create    — 등록 폼 샘플 (기존 파일 없음)
    //   · /admin/system/file/demo/edit      — 수정 폼 샘플 (최근 파일 3건 시드)
    //   · /admin/system/file/demo/view      — 상세 보기 샘플 (readOnly)
    //   · POST /admin/system/file/demo/submit — 폼 제출 결과(fileIds) 에코백
    // ==================================================================

    @GetMapping("/demo")
    public String demoHub(Model model) {
        model.addAttribute("mode", "hub");
        return "admin/system/file/demo";
    }

    @GetMapping("/demo/create")
    public String demoCreate(Model model) {
        model.addAttribute("mode", "create");
        model.addAttribute("existing", List.of());
        return "admin/system/file/demo";
    }

    @GetMapping("/demo/edit")
    public String demoEdit(Model model) {
        FileSearch seed = new FileSearch();
        seed.setPage(1);
        seed.setPageSize(3);
        model.addAttribute("mode", "edit");
        model.addAttribute("existing", service.search(seed));
        return "admin/system/file/demo";
    }

    @GetMapping("/demo/view")
    public String demoView(Model model) {
        FileSearch seed = new FileSearch();
        seed.setPage(1);
        seed.setPageSize(3);
        model.addAttribute("mode", "view");
        model.addAttribute("existing", service.search(seed));
        return "admin/system/file/demo";
    }

    @PostMapping("/demo/submit")
    public String demoSubmit(@RequestParam(value = "attachments", required = false) String attachments,
                              @RequestParam(value = "title",       required = false) String title,
                              RedirectAttributes ra) {
        ra.addFlashAttribute("submittedTitle",       title);
        ra.addFlashAttribute("submittedAttachments", attachments);
        return "redirect:/admin/system/file/demo";
    }

    // ==================================================================
    // 관리자 전용 다운로드 — 검사 상태 무관 (검증 목적)
    // ==================================================================

    /** 원본 파일 다운로드 — ROLE_STAFF 이상, 검사 상태 무관. */
    @GetMapping("/{fileId}/download")
    public void adminDownload(@PathVariable String fileId,
                                jakarta.servlet.http.HttpServletRequest req,
                                HttpServletResponse res) {
        service.adminDownload(fileId, req, res);
    }

    /** 썸네일 — 이미지 파일의 400px JPG. 관리자 화면 <img> 표시용. */
    @GetMapping("/{fileId}/thumb")
    public void adminThumb(@PathVariable String fileId,
                             HttpServletResponse res) {
        service.downloadThumbnail(fileId, res);
    }

    @GetMapping("/{fileId}")
    public String detail(@PathVariable String fileId, Model model, RedirectAttributes ra) {
        FileItem f = service.get(fileId);
        if (f == null) {
            ra.addFlashAttribute("error", "파일을 찾을 수 없습니다.");
            return "redirect:/admin/system/file";
        }
        model.addAttribute("file", f);
        model.addAttribute("downloads", service.recentDownloads(fileId, 20));
        return "admin/system/file/detail";
    }

    @PostMapping("/{fileId}/scan-status")
    public String updateScanStatus(@PathVariable String fileId,
                                    @RequestParam("status") VirusScanStatus status,
                                    RedirectAttributes ra,
                                    HttpServletResponse res) {
        try {
            service.updateVirusScanStatus(fileId, status);
            log.info("===FILE_SCAN_STATUS_OVERRIDE ok fileId={} status={}", fileId, status);
            ra.addFlashAttribute("message", "검사 상태를 " + status.name() + " 으로 변경했습니다.");
        } catch (IllegalArgumentException ex) {
            log.warn("FILE_SCAN_STATUS_OVERRIDE fail fileId={} reason={}", fileId, ex.getMessage());
            ra.addFlashAttribute("error", ex.getMessage());
        } catch (Exception ex) {
            log.warn("FILE_SCAN_STATUS_OVERRIDE error fileId={}", fileId, ex);
            ra.addFlashAttribute("error", "상태 변경 중 오류가 발생했습니다.");
        }
        String target = "/admin/system/file/" + fileId;
        res.setHeader("HX-Redirect", target);
        return "redirect:" + target;
    }

    @DeleteMapping("/{fileId}")
    public String delete(@PathVariable String fileId,
                          RedirectAttributes ra,
                          HttpServletResponse res) {
        try {
            service.softDelete(fileId);
            log.info("===FILE_DELETE ok fileId={}", fileId);
            ra.addFlashAttribute("message", "파일을 삭제했습니다.");
            String target = "/admin/system/file";
            res.setHeader("HX-Redirect", target);
            return "redirect:" + target;
        } catch (IllegalArgumentException ex) {
            log.warn("FILE_DELETE fail fileId={} reason={}", fileId, ex.getMessage());
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/system/file/" + fileId;
        } catch (Exception ex) {
            log.warn("FILE_DELETE error fileId={} reason={}", fileId, ex.getMessage(), ex);
            ra.addFlashAttribute("error", "삭제 중 오류가 발생했습니다.");
            return "redirect:/admin/system/file/" + fileId;
        }
    }
}
