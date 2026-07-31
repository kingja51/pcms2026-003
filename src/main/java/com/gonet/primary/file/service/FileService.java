package com.gonet.primary.file.service;

import com.gonet.logging.file.dto.FileDownloadLog;
import com.gonet.primary.file.dto.FileItem;
import com.gonet.primary.file.dto.FileSearch;
import com.gonet.primary.file.dto.UploadRequest;
import com.gonet.primary.file.dto.UploadResult;
import com.gonet.primary.file.dto.VirusScanStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 파일 도메인 서비스 — common 의 {@link com.gonet.common.file.service.FileUploadService} /
 * {@link com.gonet.common.file.service.FileDownloadService} 를 위임받아
 * tb_file_group/tb_file DB 기록·조회·삭제·다운로드 응답을 담당.
 *
 * <p>업로드 파이프라인(6중 방어 스택) 자체는 common 엔진이 수행. 본 서비스는
 * DB INSERT, 감사 이벤트, 조회·soft delete 만 전담한다.
 */
public interface FileService {

    // ------------------------------------------------------------------
    // 업로드 (카테고리별)
    //   directory = 저장 경로의 상위 디렉터리(엔티티타입). ETC/null 이면 생략.
    // ------------------------------------------------------------------

    /** 카테고리 무관 (allowed-extensions 전체). */
    UploadResult upload(UploadRequest req, MultipartFile file, String directory,String fileGroupId);

    /** 문서 전용 — doc-extensions. */
    UploadResult uploadDocument(UploadRequest req, MultipartFile file, String directory,String fileGroupId);

    /** 이미지 전용 — 재인코딩 + 400px 썸네일. */
    UploadResult uploadImage(UploadRequest req, MultipartFile file, String directory,String fileGroupId);

    /** 동영상 전용 — 큰 파일, 재인코딩/썸네일 skip. */
    UploadResult uploadVideo(UploadRequest req, MultipartFile file, String directory,String fileGroupId);

    // ------------------------------------------------------------------
    // 조회 + 삭제
    // ------------------------------------------------------------------

    List<FileItem> search(FileSearch search);
    int            count(FileSearch search);
    FileItem       get(String fileId);
    void           softDelete(String fileId);

    // ------------------------------------------------------------------
    // 다운로드
    // ------------------------------------------------------------------

    /**
     * {@code fileId} 로 DB 조회 후 common 엔진으로 응답 작성.
     * 검사 상태 {@code PENDING}/{@code INFECTED}/{@code ERROR} 는 403 으로 거부.
     * 감사 이벤트 {@code FILE_DOWNLOAD} + download_count 증가.
     *
     * @return 전송 바이트 수 (0 이면 404/403/304)
     */
    long download(String fileId, HttpServletRequest req, HttpServletResponse res);

    /**
     * 뷰어 전용 inline 응답 — viewer 페이지의 {@code <iframe>} / 직접 fetch 에서 사용.
     *
     * <p>{@link #download} 와 같이 권한 검증 + virus_scan_status 검사를 수행하나,
     * Content-Disposition 을 {@code inline} 으로 설정해 브라우저 다운로드 대신 인플레이스 표시.
     * download_count 증가/FileDownloadLog 기록은 하지 않음 (preview 는 다운로드와 구분).
     *
     * <p>감사 이벤트 {@code FILE_PREVIEW} 만 기록 — 누가 어떤 파일을 미리보기 했는지 추적 가능.
     *
     * @return 응답 byte 수 (0 이면 404/403/304)
     */
    long previewInline(String fileId, HttpServletRequest req, HttpServletResponse res);

    /**
     * 관리자 전용 다운로드 — 검사 상태 무관하게 허용 (검증 목적).
     *
     * <p>일반 사용자 경로 {@link #download}는 CLEAN 만 허용. 반면 관리자 화면은 PENDING/ERROR
     * 상태의 파일을 직접 열어봐서 이상 여부를 확인해야 하므로 상태 체크를 우회.
     * INFECTED 파일에는 감사 로그 {@code ADMIN_DOWNLOAD_INFECTED} 경고가 남는다.
     *
     * <p>URL 규칙 {@code /admin/system/file/**} (ROLE_STAFF 이상)에서만 접근 가능.
     */
    long adminDownload(String fileId, HttpServletRequest req, HttpServletResponse res);

    /**
     * 이미지 썸네일 다운로드 — 관리자/본문 <img> 태그에서 참조.
     * 썸네일이 없거나 이미지가 아니면 {@code 404}. 검사 상태는 제한하지 않음 —
     * 썸네일은 서버가 생성한 안전한 JPG 로 원본과 별도.
     */
    long downloadThumbnail(String fileId, HttpServletResponse res);

    /**
     * {@code fileGroupId} 에 속한 CLEAN 상태 파일들을 ZIP 으로 묶어 다운로드.
     * soft-deleted/PENDING/INFECTED/ERROR 는 자동 제외. 결과 0건이면 404.
     * 감사 이벤트 {@code FILE_DOWNLOAD_GROUP} + 포함된 각 파일 download_count 증가.
     *
     * @return 압축 후 전송 byte 수
     */
    long downloadGroup(String fileGroupId, HttpServletRequest req, HttpServletResponse res);

    /** 상세 화면용 최근 다운로드 이력 (기본 20건). */
    List<FileDownloadLog> recentDownloads(String fileId, int limit);

    /**
     * 관리자 수동 검사 상태 변경 — PENDING 재검사 유도 또는 오탐 보정 시 사용.
     * 감사 이벤트 {@code FILE_SCAN_STATUS_OVERRIDE} 기록.
     */
    void updateVirusScanStatus(String fileId, VirusScanStatus status);

    /**
     * 그룹의 활성 파일 목록 — virus_scan_status 무관 (PENDING/CLEAN/ERROR 모두) 노출.
     * 도메인 detail 화면에서 썸네일·다운로드 카드 표시용.
     * soft-deleted 만 제외.
     */
    List<FileItem> listGroupFiles(String fileGroupId);

    // ------------------------------------------------------------------
    // file_group 사전 생성·동기화 (도메인 Service 호출용)
    // ------------------------------------------------------------------

    /**
     * (entityType, entityId) 의 {@code tb_file_group} 을 정책({@code downloadAuth}) 과 함께
     * 보장한다. 옵션 A 패턴 — 도메인 Service 가 picker 업로드 전에 그룹을 사전 생성하거나,
     * 사후 cascade 로 정책을 동기화하는 단일 진입점.
     *
     * <ul>
     *   <li>그룹 없음 → 신규 INSERT (download_auth=요청 정책)</li>
     *   <li>그룹 있음 + 정책 동일 → no-op</li>
     *   <li>그룹 있음 + 정책 다름 → updateDownloadAuth (cascade)</li>
     * </ul>
     *
     * @param entityType    예: "POPUP" / "BANNER" / "BBS" / "CONTENT" 등
     * @param entityId      도메인 PK (UUID v7)
     * @param siteId        사이트 ID (NULL 허용)
     * @param downloadAuth  ANONYMOUS / ROLE_MEMBER / ROLE_EMPLOYEE / ROLE_STAFF /
     *                       OWNER_PRIVACY / ROLE_MANAGER / ROLE_ADMIN 중 하나.
     *                       null/blank → DB DEFAULT(ROLE_MEMBER) 적용
     * @return file_group_id (재사용된 ID 또는 신규 발급된 ID)
     */
    String ensureGroup(String entityType, String entityId, String siteId, String downloadAuth);

    /**
     * 특정 파일그룹의 download_auth 를 <b>그룹 ID 기준</b>으로 직접 동기화.
     *
     * <p>file-picker 가 폼에서 <b>선발급한 그룹 ID</b>로 업로드한 경우, {@link #ensureGroup}
     * (entity 조회)은 별개 그룹을 만들거나 갱신해 실제 이미지 그룹에 정책이 닿지 않을 수 있다
     * (배너 SUB_HERO 이미지 302 실측, 2026-07-07). 배너/팝업처럼 공개 노출이 필요한 도메인은
     * 저장 시 이 메서드로 자신의 {@code file_group_id} 에 직접 정책을 건다.
     * 그룹 미존재/blank 는 no-op.
     */
    void updateGroupDownloadAuth(String fileGroupId, String downloadAuth);
}
