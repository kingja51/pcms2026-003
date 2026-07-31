package com.gonet.logging.file.service;

import com.gonet.primary.file.dto.FileItem;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 파일 다운로드 이력 기록 서비스 — log_file_download 에 INSERT.
 *
 * <p>특징:
 * <ul>
 *   <li>logging DataSource 전용 트랜잭션 (REQUIRES_NEW) — 파일 응답 트랜잭션과 완전 분리</li>
 *   <li>예외 발생 시 로그만 남기고 응답은 정상 진행 (로깅 실패가 기능을 막지 않음)</li>
 *   <li>actor 는 {@link SecurityContextHolder} 에서 즉석 추출</li>
 *   <li>파일 스냅샷(originalName/extension/sizeBytes) 저장 — 파일 삭제 후에도 조회 가능</li>
 * </ul>
 *
 * <p>eGov 호환성: 인터페이스 + Impl 분리, Impl 은 {@code EgovAbstractServiceImpl} 상속.
 * 주입은 이 인터페이스 타입으로 받을 것.
 */
public interface FileDownloadLogger {

    /** 다운로드 유형 — tb/log 구분 + 집계용. */
    enum DownloadType { SINGLE, GROUP_ZIP, ADMIN }

    /** 단일 파일 다운로드 기록. sentBytes 는 Range 시 부분일 수 있음. */
    void logSingle(FileItem f, DownloadType type, long sentBytes, HttpServletRequest req);

    /** 그룹 ZIP 다운로드 기록. */
    void logGroup(String fileGroupId, long zipBytes, HttpServletRequest req);
}
