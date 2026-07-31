package com.gonet.primary.file.controller;

import com.gonet.common.dto.ApiResponse;
import com.gonet.common.file.security.UnauthenticatedUploadException;
import com.gonet.common.file.security.UploadValidationException;
import com.gonet.primary.file.dto.UploadRequest;
import com.gonet.primary.file.dto.UploadResult;
import com.gonet.primary.file.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 파일 REST API — 업로드(카테고리별) + 다운로드.
 *
 * <p>URL:
 * <ul>
 *   <li>{@code POST /api/v1/file/upload}           — 관리자 범용 (allowed-extensions 전체)</li>
 *   <li>{@code POST /api/v1/file/upload/document}  — 문서 전용</li>
 *   <li>{@code POST /api/v1/file/upload/image}     — 이미지 전용 (썸네일 자동 생성)</li>
 *   <li>{@code POST /api/v1/file/upload/video}     — 동영상 전용 (큰 파일, 썸네일 skip)</li>
 *   <li>{@code GET  /api/v1/file/{id}/download}    — Range/ETag 지원 다운로드</li>
 * </ul>
 *
 * <p>{@code /fileDown/{id}} 별칭도 제공 — 짧은 URL 선호 도메인(게시판 등) 용.
 */
@Tag(name = "File", description = "파일 업로드(카테고리별) / 다운로드(Range/ETag/Conditional GET) REST API. " +
    "업로드는 6중 방어 스택을 거쳐 격리 저장소에 저장된 후 ClamAV 큐로 비동기 검사.")
@RestController
@RequestMapping("/api/v1/file")
public class FileApiController {

    private static final Logger log = LoggerFactory.getLogger(FileApiController.class);

    private final FileService service;

    public FileApiController(FileService service) {
        this.service = service;
    }

    // ==================================================================
    // 업로드
    // ==================================================================

    @Operation(
        summary = "파일 업로드 — 범용 (allowed-extensions 전체)",
        description = "관리자용 범용 업로드. 6중 방어 스택 통과 후 격리 저장소에 저장 + ClamAV 큐 enqueue. " +
            "응답의 `virusScanStatus=PENDING` 은 비동기 검사 대기를 의미하며, NoOp 모드(clamav.enabled=false) 환경에서도 " +
            "PENDING 상태로 다운로드 허용 — `tb_file_group.download_auth` 정책이 별도로 적용됨.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "업로드 성공 (검사 대기)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "유효성 위반 — 확장자/MIME/사이즈 정책 위반, BindingResult 에러",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "비로그인 — 본 도메인은 인증 필수"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 오류 — 격리 저장소 IO 실패 등")
    })
    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<UploadResult>> uploadAny(
            @Valid @ModelAttribute UploadRequest req, BindingResult br,
            @Parameter(description = "업로드 파일", required = true)
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "저장 하위 디렉토리(선택). 미지정 시 카테고리 기본 디렉토리.")
            @RequestParam(value = "directory", required = false) String directory,
            @Parameter(description = "기존 file_group 에 추가할 때 그룹 ID(선택). 미지정 시 신규 그룹 생성.")
            String fileGroupId) {
        return doUpload("ANY", req, br, file, directory,fileGroupId);
    }

    @Operation(summary = "파일 업로드 — 문서 카테고리",
        description = "문서 확장자(pdf/doc/docx/xls/xlsx/ppt/pptx/hwp 등) 화이트리스트만 허용.")
    @PostMapping("/upload/document")
    public ResponseEntity<ApiResponse<UploadResult>> uploadDocument(
            @Valid @ModelAttribute UploadRequest req, BindingResult br,
            @Parameter(description = "업로드 파일 (문서만)", required = true)
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "directory", required = false) String directory, String fileGroupId) {
        return doUpload("DOC", req, br, file, directory,fileGroupId);
    }

    @Operation(summary = "파일 업로드 — 이미지 카테고리",
        description = "이미지 확장자(jpg/png/gif/webp/svg 등) 화이트리스트. " +
            "Thumbnailator 로 안전 재인코딩 — 폴리글랏 공격 방어. 썸네일도 자동 생성.")
    @PostMapping("/upload/image")
    public ResponseEntity<ApiResponse<UploadResult>> uploadImage(
            @Valid @ModelAttribute UploadRequest req, BindingResult br,
            @Parameter(description = "업로드 이미지", required = true)
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "directory", required = false) String directory, String fileGroupId) {
        return doUpload("IMAGE", req, br, file, directory,fileGroupId);
    }

    @Operation(summary = "파일 업로드 — 동영상 카테고리",
        description = "동영상 확장자(mp4/webm/mov 등) 화이트리스트. 큰 파일 대비 썸네일 자동생성은 skip.")
    @PostMapping("/upload/video")
    public ResponseEntity<ApiResponse<UploadResult>> uploadVideo(
            @Valid @ModelAttribute UploadRequest req, BindingResult br,
            @Parameter(description = "업로드 동영상", required = true)
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "directory", required = false) String directory, String fileGroupId) {
        return doUpload("VIDEO", req, br, file, directory,fileGroupId);
    }

    private ResponseEntity<ApiResponse<UploadResult>> doUpload(
            String category, UploadRequest req, BindingResult br,
            MultipartFile file, String directory, String fileGroupId) {
        if (br.hasErrors()) {
            String firstError = br.getFieldError() != null
                ? br.getFieldError().getDefaultMessage()
                : "요청 파라미터가 올바르지 않습니다.";
            return ResponseEntity.badRequest().body(ApiResponse.fail(firstError));
        }
        try {
            UploadResult result = switch (category) {
                case "DOC"   -> service.uploadDocument(req, file, directory,fileGroupId);
                case "IMAGE" -> service.uploadImage(req, file, directory,fileGroupId);
                case "VIDEO" -> service.uploadVideo(req, file, directory,fileGroupId);
                default      -> service.upload(req, file, directory,fileGroupId);
            };
            return ResponseEntity
                .status(org.springframework.http.HttpStatus.CREATED)
                .body(ApiResponse.ok("업로드 완료. 바이러스 검사 대기 중.", result));
        } catch (UnauthenticatedUploadException ex) {
            log.info("===FILE_UPLOAD denied-unauth category={}", category);
            return ResponseEntity
                .status(org.springframework.http.HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.fail(ex.getMessage()));
        } catch (UploadValidationException ex) {
            log.info("===FILE_UPLOAD rejected category={} reason={}", category, ex.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.fail(ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            log.warn("FILE_UPLOAD fail category={} reason={}", category, ex.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.fail(ex.getMessage()));
        } catch (Exception ex) {
            log.warn("FILE_UPLOAD error category={} reason={}", category, ex.getMessage(), ex);
            return ResponseEntity.internalServerError()
                .body(ApiResponse.fail("업로드 처리 중 오류가 발생했습니다."));
        }
    }

    // ==================================================================
    // 다운로드 — Range/ETag/Conditional GET/HEAD
    // ==================================================================

    @Operation(
        summary = "파일 다운로드 (단건)",
        description = """
            HTTP 조건부 GET / Range 요청 지원:
            * `Range` — 부분 다운로드 (RFC 7233)
            * `If-None-Match` / `If-Modified-Since` — 304 Not Modified
            * `If-Match` — 412 Precondition Failed
            * `If-Range` — 검증자 일치 시만 206, 불일치면 200 으로 강등

            응답 헤더: `ETag` / `Last-Modified` / `Cache-Control: private, no-store` / `Accept-Ranges: bytes`.
            HEAD 메서드는 동일 헤더만 반환(본문 skip).

            권한: `tb_file_group.download_auth` 정책에 따라 Service 단에서 평가.
            `INFECTED` / `ERROR` 상태 파일은 차단 — `PENDING` / `CLEAN` 만 다운로드 허용.""")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "본문 또는 304 헤더"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "206", description = "Partial Content (Range)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "304", description = "Not Modified — 캐시된 버전 유효"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "비로그인 — download_auth 가 익명 비허용"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 부족 — 회원 요구 vs 비회원 등"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "파일 미존재 / soft-deleted"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "412", description = "If-Match 조건 위반"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "416", description = "Range 표현이 파일 범위 외")
    })
    @RequestMapping(value = "/{fileId}/download",
        method = {RequestMethod.GET, RequestMethod.HEAD})
    public void download(
            @Parameter(description = "파일 ID (UUID v7)", required = true,
                example = "019d9b80-0b79-780c-be2a-264a22b14848")
            @PathVariable String fileId,
            HttpServletRequest req, HttpServletResponse res) {
        service.download(fileId, req, res);
    }

    @Operation(
        summary = "파일 그룹 ZIP 다운로드",
        description = "지정 그룹의 CLEAN 파일만 묶어 ZIP 으로 반환. INFECTED/ERROR 파일은 제외. " +
            "Content-Type: `application/zip`. 파일명: `{groupId}.zip`.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "ZIP 본문"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "그룹 미존재 / 모든 파일이 다운로드 불가")
    })
    @RequestMapping(value = "/group/{fileGroupId}/download",
        method = {RequestMethod.GET, RequestMethod.HEAD})
    public void downloadGroup(
            @Parameter(description = "파일 그룹 ID (UUID v7)", required = true)
            @PathVariable String fileGroupId,
            HttpServletRequest req, HttpServletResponse res) {
        service.downloadGroup(fileGroupId, req, res);
    }
}
