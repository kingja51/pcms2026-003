package com.gonet.common.file.service;

import com.gonet.common.file.dto.ZipEntrySpec;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.List;

/**
 * 도메인-독립 파일 다운로드 엔진 — Range/ETag/Content-Disposition 처리.
 *
 * <p>도메인 측(primary/file, 게시판, 기타)은 자체 DB 조회로 찾아낸 파일 메타데이터를
 * 넘겨주면 본 엔진이 바이트 전송·조건부 응답(304)·부분 응답(206)을 전담한다.
 */
public interface FileDownloadService {

    /**
     * 정식 저장소(root) 기준 상대경로에 있는 파일을 내려보낸다.
     *
     * <p>응답 헤더:
     * <ul>
     *   <li>{@code Content-Type} — 저장 시 검출된 MIME (Tika). 신뢰 가능한 값</li>
     *   <li>{@code Content-Length} — 바이트 크기 (Range 시 부분)</li>
     *   <li>{@code Content-Disposition} — {@code attachment; filename="<원본명>"} (RFC 5987 UTF-8)</li>
     *   <li>{@code ETag} — SHA-256 기반 unique. 클라이언트의 {@code If-None-Match} 가 일치하면 304</li>
     *   <li>{@code Accept-Ranges: bytes} — Range 요청 지원 명시</li>
     * </ul>
     *
     * <p>부분 응답(206): {@code Range: bytes=start-end} 헤더가 들어오면 해당 구간만 전송.
     *
     * @param relative     정식 root 기준 상대경로 (tb_file.stored_path)
     * @param originalName 다운로드 파일명 (Content-Disposition)
     * @param mime         Content-Type 값 (저장 시 Tika 검출 MIME)
     * @param fileHash     SHA-256 소문자 64자 — ETag 생성용
     * @param req/res      HttpServlet 객체
     * @return 응답에 쓴 바이트 수 (다운로드 카운트 용)
     */
    long download(String relative, String originalName, String mime, String fileHash,
                   HttpServletRequest req, HttpServletResponse res);

    /**
     * inline disposition 변형 — Content-Disposition 을 {@code attachment} 대신
     * {@code inline} 으로 송출. 브라우저가 다운로드 대신 <b>인플레이스 표시</b> 한다
     * (PDF 뷰어, 텍스트 표시 등). 그 외 헤더/캐싱/Range 처리는 동일.
     *
     * <p>뷰어 페이지가 {@code <iframe src=".../inline">} 로 사용. download_count 증가는
     * 도메인 측 책임 (preview 는 보통 카운트하지 않음).
     */
    long download(String relative, String originalName, String mime, String fileHash,
                   HttpServletRequest req, HttpServletResponse res,
                   boolean inline);

    /**
     * 여러 파일을 ZIP 으로 묶어 스트리밍 다운로드.
     *
     * <p>응답:
     * <ul>
     *   <li>{@code Content-Type: application/zip}</li>
     *   <li>{@code Content-Disposition: attachment; filename*=UTF-8''<archiveName>.zip}</li>
     *   <li>{@code Accept-Ranges: none} — 동적 생성이라 Range 미지원</li>
     * </ul>
     *
     * <p>동작: {@code ZipOutputStream} 을 response OutputStream 에 연결해 전체 메모리에 쌓지
     * 않고 스트리밍. 동일 entryName 이 2회 이상이면 {@code " (2)"}, {@code " (3)"} 접미사로
     * 중복 회피 (확장자 보존). 각 entry 의 storedPath 가 실제 파일이 아니면 건너뛰고 계속.
     *
     * <p>entries 가 비어있으면 {@code 404}. 하나라도 있으면 200 + zip 스트림.
     *
     * @return 생성된 zip 의 총 byte 수 (압축 후)
     */
    long downloadZip(List<ZipEntrySpec> entries, String archiveName,
                      HttpServletRequest req, HttpServletResponse res);

    /**
     * 썸네일(JPG) 다운로드 — thumbnail root 기준 상대 경로로 접근.
     *
     * <p>응답:
     * <ul>
     *   <li>{@code Content-Type: image/jpeg}</li>
     *   <li>{@code Cache-Control: private, max-age=3600} — 브라우저 캐시 활용</li>
     *   <li>{@code X-Content-Type-Options: nosniff}</li>
     * </ul>
     *
     * <p>{@code thumbnailRelative} 가 null/blank 또는 파일이 없으면 {@code 404}.
     *
     * @return 전송 byte 수
     */
    long downloadThumbnail(String thumbnailRelative, HttpServletResponse res);
}
