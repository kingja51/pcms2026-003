package com.gonet.primary.file.service;

import com.gonet.common.web.ResourceNotFoundException;
import com.gonet.common.audit.AuditEvent;
import com.gonet.common.audit.AuditLogger;
import com.gonet.common.file.dto.UploadCommit;
import com.gonet.common.file.dto.ZipEntrySpec;
import com.gonet.common.file.security.FileStorage;
import com.gonet.common.file.security.UploadValidationException;
import com.gonet.common.file.service.DocumentConvertService;
import com.gonet.common.file.service.FileDownloadService;
import com.gonet.common.file.service.FileUploadService;
import com.gonet.logging.file.dto.FileDownloadLog;
import com.gonet.logging.file.mapper.FileDownloadLogMapper;
import com.gonet.logging.file.service.FileDownloadLogger;
import com.gonet.logging.security.dto.SecurityEvent;
import com.gonet.logging.security.dto.SecurityEventType;
import com.gonet.logging.security.service.SecurityLogger;
import com.gonet.common.util.JsonUtils;
import com.gonet.common.util.UuidV7Generator;
import com.gonet.config.datasource.PrimaryDataSourceConfig;
import com.gonet.primary.file.dto.FileEntityType;
import com.gonet.primary.file.dto.DownloadAuth;
import com.gonet.primary.file.dto.FileGroup;
import com.gonet.primary.file.dto.FileItem;
import com.gonet.primary.file.dto.FileSearch;
import com.gonet.primary.file.dto.UploadRequest;
import com.gonet.primary.file.dto.UploadResult;
import com.gonet.primary.file.dto.VirusScanStatus;
import com.gonet.primary.file.mapper.FileGroupMapper;
import com.gonet.primary.file.mapper.FileMapper;
import com.gonet.primary.board.article.service.BoardArticleService;
import com.gonet.primary.system.login.dto.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.egovframe.rte.fdl.cmmn.EgovAbstractServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Objects;

/**
 * Primary 파일 서비스 — common 엔진 위임 + tb_file_group/tb_file 기록.
 *
 * <p>업로드는 {@link FileUploadService} 가 6중 방어 스택을 수행하고, 본 서비스는 결과
 * ({@link UploadCommit})를 tb_file 에 INSERT + 감사 이벤트 발행. 다운로드는
 * {@link FileDownloadService} 가 Range/ETag/Content-Disposition 을 처리.
 */
@Service
@Transactional(readOnly = true, transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
public class FileServiceImpl extends EgovAbstractServiceImpl implements FileService {

    private static final Logger log = LoggerFactory.getLogger(FileServiceImpl.class);

    private final FileMapper             fileMapper;
    private final FileGroupMapper        fileGroupMapper;
    private final FileUploadService      uploadEngine;
    private final FileDownloadService    downloadEngine;
    private final AsyncThumbnailService  asyncThumbnail;
    private final FileDownloadLogger     downloadLogger;
    private final FileDownloadLogMapper  downloadLogMapper;
    private final AuditLogger            auditLogger;
    private final FileStorage            storage;
    private final SecurityLogger         securityLogger;
    /** PPTX → PDF 변환 (LibreOffice). gopcms.file.converter.enabled=false 면 null. Optional 사용. */
    private final DocumentConvertService docConverter;
    /** OWNER_PRIVACY 정책의 본인 검증용 — BBS article 의 created_by lookup. @Lazy 로 미사용시 부팅 영향 0. */
    private final BoardArticleService    boardArticleService;
    /** 검색 색인 동기화 — 업로드/삭제 직후 호출. circular 방지용 @Lazy. */

    /**
     * ROLE_PRIVACY role_id (UUID v7). 2026-05-01 DDL seed 기본값.
     * 환경별로 다르게 부여한 사이트는 application.yml 로 오버라이드.
     */
    private final String rolePrivacyId;

    public FileServiceImpl(FileMapper fileMapper,
                           FileGroupMapper fileGroupMapper,
                           FileUploadService uploadEngine,
                           FileDownloadService downloadEngine,
                           AsyncThumbnailService asyncThumbnail,
                           FileDownloadLogger downloadLogger,
                           FileDownloadLogMapper downloadLogMapper,
                           AuditLogger auditLogger,
                           FileStorage storage,
                           SecurityLogger securityLogger,
                           @Autowired(required = false) DocumentConvertService docConverter,
                           @Lazy BoardArticleService boardArticleService,
                           @Value("${gopcms.security.role-privacy-id:00000000-0000-7000-8000-000000000018}")
                           String rolePrivacyId) {
        this.fileMapper          = fileMapper;
        this.fileGroupMapper     = fileGroupMapper;
        this.uploadEngine        = uploadEngine;
        this.downloadEngine      = downloadEngine;
        this.asyncThumbnail      = asyncThumbnail;
        this.downloadLogger      = downloadLogger;
        this.downloadLogMapper   = downloadLogMapper;
        this.auditLogger         = auditLogger;
        this.storage             = storage;
        this.securityLogger      = securityLogger;
        this.docConverter        = docConverter;
        this.boardArticleService = boardArticleService;
        this.rolePrivacyId       = rolePrivacyId;
    }

    // ------------------------------------------------------------------
    // 조회
    // ------------------------------------------------------------------

    @Override
    public List<FileItem> search(FileSearch search) {
        return fileMapper.findList(search == null ? new FileSearch() : search);
    }

    @Override
    public int count(FileSearch search) {
        return fileMapper.countList(search == null ? new FileSearch() : search);
    }

    @Override
    public FileItem get(String fileId) {
        if (fileId == null || fileId.isBlank()) return null;
        return fileMapper.findById(fileId);
    }

    // ------------------------------------------------------------------
    // 업로드 — common 엔진 위임
    // ------------------------------------------------------------------

    // 업로드 4종은 READ_COMMITTED 필수 — 다중 파일 첨부 시 file-picker 가 같은
    // fileGroupId 로 병렬 POST 하고, commit() 의 tb_file_group upsert(ODKU)가 동일 행을
    // 동시 갱신한다. MariaDB 11.6+ 는 innodb_snapshot_isolation=ON(11.8 기본)이라
    // REPEATABLE READ 스냅숏이 낡은 트랜잭션의 갱신을 1020(Record has changed since
    // last read)으로 거부 → 첫 파일만 성공하고 나머지 업로드 실패. READ_COMMITTED 는
    // 문장 단위 스냅숏이라 행 잠금 대기 후 정상 갱신된다.
    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        isolation = Isolation.READ_COMMITTED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public UploadResult upload(UploadRequest req, MultipartFile file, String directory, String fileGroupId) {
        try {
            return commit(req, uploadEngine.uploadAny(file, resolveDirectory(req, directory)), fileGroupId);
        } catch (UploadValidationException ex) {
            logUploadBlock("ANY", file, ex); throw ex;
        }
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        isolation = Isolation.READ_COMMITTED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public UploadResult uploadDocument(UploadRequest req, MultipartFile file, String directory, String fileGroupId) {
        try {
            return commit(req, uploadEngine.uploadDocument(file, resolveDirectory(req, directory)), fileGroupId);
        } catch (UploadValidationException ex) {
            logUploadBlock("DOC", file, ex); throw ex;
        }
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        isolation = Isolation.READ_COMMITTED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public UploadResult uploadImage(UploadRequest req, MultipartFile file, String directory, String fileGroupId) {
        try {
            return commit(req, uploadEngine.uploadImage(file, resolveDirectory(req, directory)), fileGroupId);
        } catch (UploadValidationException ex) {
            logUploadBlock("IMAGE", file, ex); throw ex;
        }
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        isolation = Isolation.READ_COMMITTED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public UploadResult uploadVideo(UploadRequest req, MultipartFile file, String directory, String fileGroupId) {
        try {
            return commit(req, uploadEngine.uploadVideo(file, resolveDirectory(req, directory)), fileGroupId);
        } catch (UploadValidationException ex) {
            logUploadBlock("VIDEO", file, ex); throw ex;
        }
    }

    /**
     * 6중 방어 스택의 1·2·3·4 단계가 차단한 업로드를 log_security 에 적재 (§0.48).
     *
     * <p>WARN severity. 차단 사유는 {@link UploadValidationException#getMessage()} —
     * 더블 확장자 / null byte / dangerous extension / MIME mismatch / 카테고리 불일치 등.
     * 차단 후 본 트랜잭션은 어차피 롤백되므로 SecurityLogger 의 REQUIRES_NEW 분리 TX 가 필수.
     */
    private void logUploadBlock(String category, MultipartFile file, UploadValidationException ex) {
        try {
            String original = (file == null) ? null : file.getOriginalFilename();
            long size = (file == null) ? 0L : file.getSize();
            securityLogger.write(SecurityEvent.of(SecurityEventType.UPLOAD_BLOCK)
                .withDetail("{\"category\":"     + JsonUtils.quote(category)
                    + ",\"original\":"            + JsonUtils.quote(original)
                    + ",\"size\":"                + size
                    + ",\"reason\":"              + JsonUtils.quote(ex.getMessage()) + "}"));
        } catch (Exception ignored) {
            // 보안 적재 실패가 차단 응답을 막지 않도록 무시 — fallback logger 가 흡수.
        }
    }

    /**
     * directory 우선순위: 명시 파라미터 → UploadRequest.entityType → ETC.
     * ETC/null/blank 는 FileStorage 가 자동으로 경로에서 생략.
     */
    private static String resolveDirectory(UploadRequest req, String directory) {
        if (directory != null && !directory.isBlank()) return directory.trim();
        if (req != null && req.getEntityType() != null && !req.getEntityType().isBlank()) {
            return req.getEntityType().trim();
        }
        return FileEntityType.ETC.name();
    }

    private UploadResult commit(UploadRequest req, UploadCommit c, String fileGroupId) {
        Objects.requireNonNull(req, "UploadRequest");
        Objects.requireNonNull(c,   "UploadCommit");

        String entityTypeName = FileEntityType.safeParse(req.getEntityType()).name();
        String entityId = (req.getEntityId() == null || req.getEntityId().isBlank())
            ? UuidV7Generator.generate()  // 호출자 미제공 시 fallback — 도메인 prefix 없음
            : req.getEntityId().trim();

        FileGroup group = (fileGroupId == null || fileGroupId.isBlank())
            ? null
            : fileGroupMapper.findById(fileGroupId);

        if (group == null) {
            group = new FileGroup();
            // 호출자가 그룹 미지정(첫 업로드) 시 신규 발급 — null 그대로 insert 하면
            // file_group_id NOT NULL 위반 (원본 잠재 결함, ensureGroupFor 와 동일 프리픽스)
            group.setFileGroupId(fileGroupId == null || fileGroupId.isBlank()
                ? UuidV7Generator.generate("FG0")
                : fileGroupId);
            group.setEntityType(entityTypeName);
            group.setEntityId(entityId);
            group.setSiteId(req.getSiteId());
            fileGroupMapper.insert(group);
        }

        FileItem item = new FileItem();
        item.setFileId(UuidV7Generator.generate("FIL"));
        item.setFileGroupId(group.getFileGroupId());
        item.setOriginalName(c.getOriginalName());
        item.setStoredName(c.getStoredName());
        item.setStoredPath(c.getStoredPath());
        // 썸네일은 업로드 완료 후 비동기 생성 — 지금은 NULL 삽입
        item.setThumbnailPath(null);
        item.setExtension(c.getExtension());
        item.setMimeDetected(c.getMimeDetected());
        item.setMimeClient(c.getMimeClient());
        item.setSizeBytes(c.getSizeBytes());
        item.setFileHash(c.getFileHash());
        item.setIsImageYn(c.isImage() ? "Y" : "N");
        item.setReencodedYn(c.isReencoded() ? "Y" : "N");
        item.setVirusScanStatus(VirusScanStatus.PENDING.name());
        // 클라이언트(file-picker) 가 보낸 업로드 순서 사용. 미전송/음수면 0.
        Integer reqSortOrder = req.getSortOrder();
        item.setSortOrder(reqSortOrder == null || reqSortOrder < 0 ? 0 : reqSortOrder);
        fileMapper.insert(item);

        // 이미지면 비동기 썸네일 생성 — 대용량/다수 파일 업로드 응답 지연 완화.
        // 별도 스레드풀(thumbnailExecutor)에서 수행 후 tb_file.thumbnail_path UPDATE.
        // 실패해도 업로드는 이미 커밋됨 — 재생성 배치로 복구 가능.
        if (c.isImage()) {
            asyncThumbnail.generateAndSave(
                item.getFileId(),
                item.getStoredPath(),
                item.getExtension(),
                entityTypeName,
                item.getStoredName());
        }

        // ClamAV 바이러스 스캔은 별도 JAR Daemon (D:/claude/ClamAV) 이 tb_file 의
        // virus_scan_status='PENDING' 행을 폴링하여 처리한다. 본 프로세스는 INSERT 만 하고
        // 어떤 hand-off 호출도 하지 않음.

        auditLogger.write(AuditEvent.of("FILE_UPLOAD", "tb_file")
            .withTarget(item.getFileId())
            .withAfter("{\"originalName\":" + JsonUtils.quote(item.getOriginalName())
                + ",\"sizeBytes\":"    + c.getSizeBytes()
                + ",\"extension\":"    + JsonUtils.quote(c.getExtension())
                + ",\"mimeDetected\":" + JsonUtils.quote(c.getMimeDetected())
                + ",\"category\":\""   + c.getCategory() + "\""
                + ",\"reencoded\":"    + c.isReencoded()
                + ",\"thumbnail\":"    + (c.getThumbnailPath() != null)
                + ",\"fileHash\":"     + JsonUtils.quote(c.getFileHash()) + "}")
            .withResult("SUCCESS"));

        // 검색 색인 훅 없음 — 검색은 외부 엔진(contextPath=/search)이 담당한다(D10)

        return new UploadResult(item.getFileId(), group.getFileGroupId(),
            item.getOriginalName(), c.getExtension(), c.getMimeDetected(),
            c.getSizeBytes(), c.getFileHash(), c.isReencoded(), VirusScanStatus.PENDING);
    }

    // ------------------------------------------------------------------
    // 다운로드 — common 엔진 위임
    // ------------------------------------------------------------------

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public long download(String fileId, HttpServletRequest req, HttpServletResponse res) {
        FileItem f = fileMapper.findById(fileId);
        if (f == null) {
            res.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return 0;
        }
        VirusScanStatus status = f.scanStatusEnum();
        if (!status.isDownloadable()) {
            log.info("===FILE_DOWNLOAD_BLOCKED fileId={} status={}", fileId, status);
            res.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return 0;
        }

        // file_group.download_auth 권한 검증 — Spring Security ExceptionTranslationFilter
        // 가 비로그인은 EntryPoint(로그인 redirect), 인증됨+거부는 AccessDeniedHandler(403)
        // 로 분기하도록 표준 예외를 던진다.
        enforceDownloadAuth(f.getFileGroupId(), fileId);

        long sent = downloadEngine.download(
            f.getStoredPath(), f.getOriginalName(),
            f.getMimeDetected(), f.getFileHash(),
            req, res);
        if (sent > 0) {
            fileMapper.incrementDownloadCount(fileId);
            auditLogger.write(AuditEvent.of("FILE_DOWNLOAD", "tb_file")
                .withTarget(fileId)
                .withAfter("{\"sizeBytes\":" + sent + "}")
                .withResult("SUCCESS"));
            downloadLogger.logSingle(f, FileDownloadLogger.DownloadType.SINGLE, sent, req);
        }
        return sent;
    }

    @Override
    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public long previewInline(String fileId, HttpServletRequest req, HttpServletResponse res) {
        FileItem f = fileMapper.findById(fileId);
        if (f == null) {
            res.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return 0;
        }
        VirusScanStatus status = f.scanStatusEnum();
        if (!status.isDownloadable()) {
            log.info("===FILE_PREVIEW_BLOCKED fileId={} status={}", fileId, status);
            res.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return 0;
        }

        // 다운로드와 동일한 권한 정책 적용 — preview 도 같은 보호 등급.
        enforceDownloadAuth(f.getFileGroupId(), fileId);

        // PPTX·DOC·DOCX 는 LibreOffice 서버 변환 → PDF inline 응답.
        // 첫 요청 시 ~5~30초 동기 변환, 이후 캐시 hit 즉시 응답.
        // 컨버터 미주입(설정 비활성/LibreOffice 미설치) 시 일반 응답 폴백.
        if (docConverter != null && needsLibreOfficeConvert(f)) {
            long sent = sendConvertedPdf(f, req, res);
            if (sent >= 0) return sent;
            // 변환 실패 시 폴백 처리 (sent==-1) — fall through 안 함, 이미 응답 종료
            return 0;
        }

        long sent = downloadEngine.download(
            f.getStoredPath(), f.getOriginalName(),
            f.getMimeDetected(), f.getFileHash(),
            req, res, /*inline=*/ true);
        if (sent > 0) {
            // download_count 미증가, FileDownloadLog 미기록 — preview 는 다운로드와 구분.
            auditLogger.write(AuditEvent.of("FILE_PREVIEW", "tb_file")
                .withTarget(fileId)
                .withAfter("{\"sizeBytes\":" + sent + "}")
                .withResult("SUCCESS"));
        }
        return sent;
    }

    /**
     * 확장자 또는 파일명 기준 PPTX 판정 (backup — 기존 단독 PPTX 체크).
     * 현재는 {@link #needsLibreOfficeConvert} 로 대체됨. 회귀 참조용 보존.
     */
    private static boolean isPptx_backup(FileItem f) {
        String ext = f.getExtension();
        if (ext != null && "pptx".equalsIgnoreCase(ext.trim())) return true;
        String name = f.getOriginalName();
        return name != null && name.toLowerCase().endsWith(".pptx");
    }

    /**
     * LibreOffice 서버 변환이 필요한 형식 판정 — PPTX / DOC / DOCX.
     * extension 컬럼이 비어 있을 경우 originalName 접미사로 fallback.
     */
    private static boolean needsLibreOfficeConvert(FileItem f) {
        String ext = f.getExtension();
        if (ext != null) {
            String lower = ext.trim().toLowerCase();
            if ("pptx".equals(lower) || "docx".equals(lower) || "doc".equals(lower)) return true;
        }
        String name = f.getOriginalName();
        if (name == null) return false;
        String lower = name.toLowerCase();
        return lower.endsWith(".pptx") || lower.endsWith(".docx") || lower.endsWith(".doc");
    }

    /**
     * PPTX → LibreOffice 변환된 PDF 를 inline 응답.
     *
     * @return 전송 byte 수 ({@code >= 0}), 변환 실패로 응답 코드만 보낸 경우 0,
     *         호출 측이 fall-through 해야 하면 -1 (현재는 미사용)
     */
    private long sendConvertedPdf(FileItem f, HttpServletRequest req, HttpServletResponse res) {
        java.nio.file.Path src = storage.resolveStoredPath(f.getStoredPath());
        java.nio.file.Path pdf;
        try {
            pdf = docConverter.ensurePdf(src, f.getFileHash());
        } catch (RuntimeException ex) {
            // 전체 stack trace 로그 — 503 진단 시 JVM 콘솔에서 cause chain 추적 가능.
            log.warn("FILE_PREVIEW_CONVERT_FAIL fileId={}", f.getFileId(), ex);
            res.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return 0;
        }

        // 변환된 PDF 의 root-relative 경로를 downloadEngine 에 전달.
        // FileStorage 는 stored_path 를 root 기준 상대 경로로 해석 — 캐시 PDF 는 별도 root 라
        // 전체 절대경로를 그대로 넘기되, mime/originalName 은 PDF 로 표시.
        // (참고) downloadEngine.download() 가 storage.resolveStoredPath 를 통해 다시 변환되지만,
        // 이미 절대경로면 NIO Path.resolve 의 동작상 그대로 반환되도록 — 본 케이스는 전용 헬퍼 사용.
        long sent = streamPdfInline(pdf, f.getOriginalName(), req, res);
        if (sent > 0) {
            auditLogger.write(AuditEvent.of("FILE_PREVIEW_PDF", "tb_file")
                .withTarget(f.getFileId())
                .withAfter("{\"sizeBytes\":" + sent + ",\"converted\":true}")
                .withResult("SUCCESS"));
        }
        return sent;
    }

    /**
     * 절대 경로의 PDF 파일을 inline disposition 으로 직접 스트리밍.
     * (FileDownloadService 의 stored_path resolve 우회 — 캐시 PDF 는 다른 root 에 있음)
     */
    private long streamPdfInline(java.nio.file.Path pdfPath, String originalName,
                                  HttpServletRequest req, HttpServletResponse res) {
        try {
            if (!java.nio.file.Files.isRegularFile(pdfPath)) {
                res.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return 0;
            }
            long size = java.nio.file.Files.size(pdfPath);
            String displayName = originalName != null
                ? originalName.replaceAll("(?i)\\.(pptx|docx|doc)$", "") + ".pdf"
                : "preview.pdf";
            String safeAscii = displayName.replaceAll("[^\\x20-\\x7E]", "_")
                .replaceAll("[\"\\\\]", "_");
            String encoded = java.net.URLEncoder.encode(displayName, java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20");

            res.setStatus(HttpServletResponse.SC_OK);
            res.setContentType("application/pdf");
            res.setHeader("Content-Disposition",
                "inline; filename=\"" + safeAscii + "\"; filename*=UTF-8''" + encoded);
            res.setHeader("Cache-Control", "private, no-store");
            res.setHeader("X-Content-Type-Options", "nosniff");
            res.setContentLengthLong(size);

            if ("HEAD".equalsIgnoreCase(req.getMethod())) {
                return 0;
            }

            try (java.io.OutputStream out = res.getOutputStream()) {
                java.nio.file.Files.copy(pdfPath, out);
                out.flush();
            }
            return size;
        } catch (java.io.IOException e) {
            log.info("PDF stream error path={} reason={}", pdfPath, e.getMessage());
            return 0;
        }
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public long adminDownload(String fileId, HttpServletRequest req, HttpServletResponse res) {
        FileItem f = fileMapper.findById(fileId);
        if (f == null) {
            res.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return 0;
        }
        VirusScanStatus status = f.scanStatusEnum();
        if (status == VirusScanStatus.INFECTED) {
            log.warn("ADMIN_DOWNLOAD_INFECTED fileId={} original={} — 관리자 검증용 강제 다운로드",
                fileId, f.getOriginalName());
        }

        long sent = downloadEngine.download(
            f.getStoredPath(), f.getOriginalName(),
            f.getMimeDetected(), f.getFileHash(),
            req, res);
        if (sent > 0) {
            fileMapper.incrementDownloadCount(fileId);
            auditLogger.write(AuditEvent.of("FILE_ADMIN_DOWNLOAD", "tb_file")
                .withTarget(fileId)
                .withAfter("{\"status\":\"" + status + "\",\"sizeBytes\":" + sent + "}")
                .withResult("SUCCESS"));
            downloadLogger.logSingle(f, FileDownloadLogger.DownloadType.ADMIN, sent, req);
        }
        return sent;
    }

    @Override
    public long downloadThumbnail(String fileId, HttpServletResponse res) {
        if (fileId == null || fileId.isBlank()) {
            res.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return 0;
        }
        FileItem f = fileMapper.findById(fileId);
        if (f == null || f.getThumbnailPath() == null || f.getThumbnailPath().isBlank()) {
            res.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return 0;
        }

        // file_group.download_auth 적용 — download / previewInline / downloadGroup 과 동일 기준.
        //
        // 바이러스 게이트(isDownloadable)는 여기에 두지 않는다. 썸네일은 원본 바이트가 아니라
        // ThumbnailGenerator 가 새로 만든 JPG 라 원본의 악성 코드를 옮기지 않는다 —
        // 인터페이스 javadoc 에 명시된 의도된 예외다.
        //
        // 반면 **접근통제는 그 논리가 커버하지 않는다.** 썸네일도 내용을 드러내므로
        // MEMBER 전용 첨부의 썸네일이 익명에게 열리면 안 된다. 무매칭 DENY 시절에는
        // 도달 자체가 불가했으나 P4 에서 /fileDown/** 를 PERMIT_ALL 로 열면서
        // 이 경로만 검사 없이 남는 상태가 됐다(2026-07-31 코드 리뷰).
        enforceDownloadAuth(f.getFileGroupId(), fileId);

        return downloadEngine.downloadThumbnail(f.getThumbnailPath(), res);
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public long downloadGroup(String fileGroupId,
                                HttpServletRequest req, HttpServletResponse res) {
        if (fileGroupId == null || fileGroupId.isBlank()) {
            res.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return 0;
        }
        // 그룹 ZIP 도 동일한 download_auth 정책 적용 — group 자체 단위로 평가.
        enforceDownloadAuth(fileGroupId, fileGroupId);

        List<FileItem> items = fileMapper.findDownloadableByGroupId(fileGroupId);
        if (items == null || items.isEmpty()) {
            log.info("===FILE_DOWNLOAD_GROUP empty groupId={}", fileGroupId);
            res.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return 0;
        }

        List<ZipEntrySpec> specs = items.stream()
            .map(f -> new ZipEntrySpec(f.getStoredPath(), f.getOriginalName()))
            .toList();
        String archiveName = "files-" + shortId(fileGroupId);

        long sent = downloadEngine.downloadZip(specs, archiveName, req, res);
        if (sent > 0) {
            for (FileItem f : items) {
                try { fileMapper.incrementDownloadCount(f.getFileId()); }
                catch (Exception ex) {
                    log.warn("DOWNLOAD_COUNT bump failed fileId={} reason={}",
                        f.getFileId(), ex.getMessage());
                }
            }
            auditLogger.write(AuditEvent.of("FILE_DOWNLOAD_GROUP", "tb_file_group")
                .withTarget(fileGroupId)
                .withAfter("{\"count\":" + items.size() + ",\"zipBytes\":" + sent + "}")
                .withResult("SUCCESS"));
            downloadLogger.logGroup(fileGroupId, sent, req);
        }
        return sent;
    }

    private static String shortId(String uuid) {
        if (uuid == null) return "group";
        String clean = uuid.replace("-", "");
        return clean.length() > 8 ? clean.substring(0, 8) : clean;
    }

    @Override
    public List<FileDownloadLog> recentDownloads(String fileId, int limit) {
        if (fileId == null || fileId.isBlank()) return List.of();
        int n = (limit <= 0 || limit > 200) ? 20 : limit;
        return downloadLogMapper.findRecentByFileId(fileId, n);
    }

    @Override
    public List<FileItem> listGroupFiles(String fileGroupId) {
        if (fileGroupId == null || fileGroupId.isBlank()) return List.of();
        return fileMapper.findAllByGroupId(fileGroupId);
    }

    // ------------------------------------------------------------------
    // file_group 사전 생성·정책 동기화 (option A — 도메인 Service 호출용)
    // ------------------------------------------------------------------

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public String ensureGroup(String entityType, String entityId,
                                String siteId, String downloadAuth) {
        if (entityType == null || entityType.isBlank()) {
            throw new IllegalArgumentException("entityType required");
        }
        if (entityId == null || entityId.isBlank()) {
            throw new IllegalArgumentException("entityId required");
        }
        String policy = (downloadAuth == null || downloadAuth.isBlank())
            ? "ROLE_MEMBER" : downloadAuth.trim();
        String et = entityType.trim();
        String eid = entityId.trim();

        FileGroup g = fileGroupMapper.findByEntity(et, eid);
        if (g != null) {
            if (!policy.equals(g.getDownloadAuth())) {
                fileGroupMapper.updateDownloadAuth(g.getFileGroupId(), policy, null, null);
                log.info("===FILE_GROUP_AUTH_SYNC groupId={} {}->{}",
                    g.getFileGroupId(), g.getDownloadAuth(), policy);
            }
            return g.getFileGroupId();
        }
        g = new FileGroup();
        g.setFileGroupId(UuidV7Generator.generate("FG0"));
        g.setEntityType(et);
        g.setEntityId(eid);
        g.setSiteId(siteId);
        g.setDownloadAuth(policy);
        g.setDeleteYn("N");
        fileGroupMapper.insert(g);
        log.info("===FILE_GROUP_CREATE groupId={} entity={}/{} auth={}",
            g.getFileGroupId(), et, eid, policy);
        return g.getFileGroupId();
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public void updateGroupDownloadAuth(String fileGroupId, String downloadAuth) {
        if (fileGroupId == null || fileGroupId.isBlank()) return;
        if (downloadAuth == null || downloadAuth.isBlank()) return;
        FileGroup g = fileGroupMapper.findById(fileGroupId);
        if (g == null || downloadAuth.trim().equals(g.getDownloadAuth())) return;
        fileGroupMapper.updateDownloadAuth(fileGroupId, downloadAuth.trim(), null, null);
        log.info("===FILE_GROUP_AUTH_SYNC_BY_ID groupId={} {}->{}",
            fileGroupId, g.getDownloadAuth(), downloadAuth.trim());
    }

    // ------------------------------------------------------------------
    // 삭제 (DB soft delete — 물리 파일은 FIM 유지)
    // ------------------------------------------------------------------

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public void softDelete(String fileId) {
        if (fileId == null || fileId.isBlank()) {
            throw new IllegalArgumentException("fileId required");
        }
        FileItem existing = fileMapper.findById(fileId);
        if (existing == null) {
            throw new ResourceNotFoundException("이미 삭제되었거나 존재하지 않는 파일입니다.");
        }
        int affected = fileMapper.softDelete(fileId);
        if (affected == 0) {
            throw new IllegalStateException("삭제 처리 실패: " + fileId);
        }
        auditLogger.write(AuditEvent.of("FILE_DELETE", "tb_file")
            .withTarget(fileId)
            .withBefore("{\"originalName\":" + JsonUtils.quote(existing.getOriginalName())
                + ",\"sizeBytes\":" + existing.getSizeBytes() + "}")
            .withResult("SUCCESS"));

    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED,
        transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public void updateVirusScanStatus(String fileId, VirusScanStatus status) {
        if (fileId == null || fileId.isBlank()) throw new IllegalArgumentException("fileId required");
        if (status == null) throw new IllegalArgumentException("status required");
        FileItem existing = fileMapper.findById(fileId);
        if (existing == null) throw new ResourceNotFoundException("파일을 찾을 수 없습니다.");
        int affected = fileMapper.updateVirusScanStatus(fileId, status.name());
        if (affected == 0) throw new IllegalStateException("상태 변경 실패: " + fileId);
        auditLogger.write(AuditEvent.of("FILE_SCAN_STATUS_OVERRIDE", "tb_file")
            .withTarget(fileId)
            .withBefore("{\"virusScanStatus\":" + JsonUtils.quote(existing.getVirusScanStatus()) + "}")
            .withAfter("{\"virusScanStatus\":" + JsonUtils.quote(status.name()) + "}")
            .withResult("SUCCESS"));
        log.info("===FILE_SCAN_STATUS_OVERRIDE fileId={} {} -> {}", fileId, existing.getVirusScanStatus(), status);
    }

    // ------------------------------------------------------------------
    // 다운로드 권한 평가 — file_group.download_auth 기반
    // ------------------------------------------------------------------

    /**
     * file_group 의 download_auth 정책을 현재 SecurityContext 의 인증 정보와 비교한다.
     * <ul>
     *   <li>{@code ANONYMOUS}     : 항상 통과</li>
     *   <li>{@code ROLE_MEMBER}   : 인증된 사용자 통과</li>
     *   <li>{@code ROLE_EMPLOYEE} : ROLE_EMPLOYEE 이상 통과</li>
     *   <li>{@code ROLE_STAFF}    : ROLE_STAFF 이상 통과</li>
     *   <li>{@code ROLE_MANAGER}  : ROLE_MANAGER 이상 통과</li>
     *   <li>{@code ROLE_ADMIN}    : ROLE_ADMIN 만 통과</li>
     *   <li><strong>{@code OWNER_PRIVACY}</strong>: 작성자 본인(BBS 의 {@code created_by})
     *       또는 ROLE_PRIVACY 명시 부여자만 통과. <em>ROLE_ADMIN 도 자동 통과 안 됨</em></li>
     * </ul>
     *
     * <p>비로그인 사용자가 ANONYMOUS 가 아닌 정책을 만나면
     * {@link InsufficientAuthenticationException} 을 던진다. Spring Security
     * ExceptionTranslationFilter 가 이를 잡아 AuthenticationEntryPoint 로 위임 →
     * 로그인 페이지로 redirect.
     *
     * <p>인증된 사용자가 권한 부족이면 {@link AccessDeniedException} → AccessDeniedHandler.
     */
    private void enforceDownloadAuth(String fileGroupId, String fileIdForLog) {
        if (fileGroupId == null || fileGroupId.isBlank()) return;
        FileGroup group = fileGroupMapper.findById(fileGroupId);
        if (group == null) return;
        String policyStr = group.getDownloadAuth();
        // 빈 값 → 통과 (기존 동작 보존 — null/blank 는 anonymous 와 동일 의미로 해석)
        if (policyStr == null || policyStr.isBlank()) return;

        DownloadAuth policy = DownloadAuth.safeParse(policyStr);
        if (policy == DownloadAuth.ANONYMOUS) return;

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean authenticated = auth != null
            && auth.isAuthenticated()
            && !(auth instanceof AnonymousAuthenticationToken);

        if (!authenticated) {
            log.info("===FILE_DOWNLOAD_AUTH_REQUIRED fileId={} policy={}", fileIdForLog, policy);
            throw new InsufficientAuthenticationException(
                "이 파일을 다운로드하려면 로그인이 필요합니다.");
        }

        // ★ OWNER_PRIVACY — 다른 정책 평가보다 먼저 처리. ROLE_ADMIN 자동 통과 금지.
        if (policy == DownloadAuth.OWNER_PRIVACY) {
            enforceOwnerPrivacy(group, fileIdForLog, auth);
            return;
        }

        if (policy == DownloadAuth.ROLE_MEMBER) return; // 인증되었으면 모두 통과

        // ROLE_EMPLOYEE / ROLE_STAFF / ROLE_MANAGER / ROLE_ADMIN — 권한 이름 매칭
        // (ROLE_ADMIN 은 모든 정책에서 자동 통과 — OWNER_PRIVACY 제외)
        String policyName = policy.name();
        boolean granted = false;
        for (GrantedAuthority ga : auth.getAuthorities()) {
            String name = ga.getAuthority();
            if ("ROLE_ADMIN".equals(name)) { granted = true; break; }
            if (policyName.equals(name))   { granted = true; break; }
        }
        if (!granted) {
            log.info("===FILE_DOWNLOAD_DENIED fileId={} policy={} authorities={}",
                fileIdForLog, policy, auth.getAuthorities());
            throw new AccessDeniedException("이 파일을 다운로드할 권한이 없습니다.");
        }
    }

    // ------------------------------------------------------------------
    // OWNER_PRIVACY 정책 평가 — 본인(BBS created_by) + ROLE_PRIVACY 만 통과
    // ------------------------------------------------------------------

    /** created_by 비교에서 본인 식별자로 인정하지 않는 sentinel 값들. */
    private static final java.util.Set<String> OWNER_GUARD_SENTINELS =
        java.util.Set.of("ANONYMOUS", "ALL", "SYSTEM");

    /**
     * OWNER_PRIVACY 통과 조건:
     * <ol>
     *   <li>현재 사용자의 role_ids CSV 에 ROLE_PRIVACY UUID 포함 → 통과</li>
     *   <li>file_group.entity_type='BBS' 인 경우, BBS article 의 created_by 가 현재
     *       사용자 user_id 와 일치 → 통과 (단 created_by 가 ""/ALL/ANONYMOUS/SYSTEM
     *       sentinel 값이면 본인으로 인정 안 함)</li>
     *   <li>그 외 → AccessDeniedException</li>
     * </ol>
     * 모든 통과 케이스는 {@code FILE_PRIVACY_DOWNLOAD} 감사 이벤트로 기록.
     */
    private void enforceOwnerPrivacy(FileGroup group, String fileIdForLog, Authentication auth) {
        Object principal = auth.getPrincipal();
        String currentUserId = (principal instanceof CustomUserDetails cud) ? cud.getUserId() : null;
        String roleIdsCsv    = (principal instanceof CustomUserDetails cud) ? cud.getRoleIds() : null;

        // (1) ROLE_PRIVACY 보유 — closure 단절이라 role_ids CSV 에 자기 자신만 포함됨
        if (rolePrivacyId != null && roleIdsCsv != null
            && containsCsvToken(roleIdsCsv, rolePrivacyId)) {
            auditPrivacyDownload(group.getFileGroupId(), currentUserId, "ROLE_PRIVACY");
            return;
        }

        // (2) BBS 작성자 본인 — entity_type='BBS' 일 때만 의미. 다른 도메인은 통과 X.
        if ("BBS".equals(group.getEntityType()) && currentUserId != null
            && !currentUserId.isBlank()) {
            String articleId = group.getEntityId();
            String writerId  = boardArticleService.findCreatedBy(articleId);
            if (writerId != null && !writerId.isBlank()
                && !OWNER_GUARD_SENTINELS.contains(writerId.toUpperCase(java.util.Locale.ROOT))
                && writerId.equals(currentUserId)) {
                auditPrivacyDownload(group.getFileGroupId(), currentUserId, "OWNER");
                return;
            }
        }

        log.info("===FILE_DOWNLOAD_DENIED_PRIVACY fileId={} groupId={} entityType={} userId={}",
            fileIdForLog, group.getFileGroupId(), group.getEntityType(), currentUserId);
        throw new AccessDeniedException("개인정보관리자 또는 본인만 다운로드할 수 있습니다.");
    }

    /** CSV 토큰 단위 정확 매치 (substring 매치 회피). */
    private static boolean containsCsvToken(String csv, String token) {
        if (csv == null || token == null) return false;
        for (String part : csv.split(",")) {
            if (token.equals(part.trim())) return true;
        }
        return false;
    }

    /** OWNER_PRIVACY 통과 시 감사 로그 기록. 이후 일반 FILE_DOWNLOAD 이벤트도 별도 발행됨. */
    private void auditPrivacyDownload(String fileGroupId, String actorUserId, String reason) {
        auditLogger.write(AuditEvent.of("FILE_PRIVACY_DOWNLOAD", "tb_file_group")
            .withTarget(fileGroupId)
            .withAfter("{\"actor\":" + JsonUtils.quote(actorUserId)
                + ",\"reason\":" + JsonUtils.quote(reason) + "}")
            .withResult("SUCCESS"));
    }
}
