package com.gonet.primary.file.controller;

import com.gonet.primary.file.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * 짧은 다운로드 URL — {@code /fileDown/{fileId}}.
 *
 * <p>게시판·CMS 본문 등에서 자주 쓰는 링크 길이를 줄이기 위한 별칭.
 * 실제 처리는 {@link FileApiController} 와 동일한 {@link FileService#download}.
 *
 * <p>본 컨트롤러는 {@code /api/v1/**} prefix 밖이라 기본 OpenAPI 그룹에는 노출되지 않지만,
 * 일관성을 위해 어노테이션을 부착해 둠 — 추후 `pathsToMatch` 확장 시 즉시 노출 가능.
 */
@Tag(name = "File Download (short URL)",
    description = "FileApiController 의 짧은 별칭. CMS 본문 / 게시판 첨부 링크 길이 단축용. " +
        "권한·Range·ETag 정책은 FileApiController 와 동일.")
@RestController
@RequestMapping("/fileDown")
public class FileDownApiController {

    private final FileService service;

    public FileDownApiController(FileService service) {
        this.service = service;
    }

    @Operation(summary = "파일 단건 다운로드 (짧은 URL)",
        description = "FileApiController.download 와 동일 — Range/ETag/Conditional GET 지원.")
    @RequestMapping(value = "/{fileId}", method = {RequestMethod.GET, RequestMethod.HEAD})
    public void download(
            @Parameter(description = "파일 ID (UUID v7)", required = true)
            @PathVariable String fileId,
            HttpServletRequest req, HttpServletResponse res) {
        service.download(fileId, req, res);
    }

    @Operation(summary = "그룹 ZIP 다운로드 (짧은 URL)",
        description = "CLEAN 파일만 묶음. HEAD 는 헤더만 반환.")
    @RequestMapping(value = "/group/{fileGroupId}", method = {RequestMethod.GET, RequestMethod.HEAD})
    public void downloadGroup(
            @Parameter(description = "파일 그룹 ID (UUID v7)", required = true)
            @PathVariable String fileGroupId,
            HttpServletRequest req, HttpServletResponse res) {
        service.downloadGroup(fileGroupId, req, res);
    }

    @Operation(summary = "썸네일 이미지",
        description = "이미지 파일의 400px JPG 썸네일. 원본이 비-이미지거나 썸네일 부재 시 404. " +
            "anonymous 허용 — 사이트 로고/공개 콘텐츠 노출용 (§0.20).")
    @GetMapping("/{fileId}/thumb")
    public void thumbnail(
            @Parameter(description = "파일 ID (UUID v7)", required = true)
            @PathVariable String fileId,
            HttpServletResponse res) {
        service.downloadThumbnail(fileId, res);
    }
}
