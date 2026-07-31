package com.gonet.common.file.service;

import java.nio.file.Path;

/**
 * 문서 변환 서비스 — PPTX/DOCX/XLSX 등을 PDF 로 변환하여 PDF.js 뷰어로 표시하기 위한 인프라.
 *
 * <p>현재 사용처: PPTX 미리보기 (클라이언트 사이드 라이브러리 정확도 한계로 LibreOffice 서버 변환 도입).
 *
 * <p>구현체는 {@link DocumentConvertServiceImpl} — JODConverter LocalOfficeManager 가 LibreOffice
 * 헤드리스 프로세스를 풀로 관리. {@code jodconverter.local.enabled=false} 면 빈 미주입 →
 * Optional&lt;DocumentConvertService&gt; 가 empty 가 되어 호출 측에서 폴백 분기.
 *
 * <p>변환 결과는 {@code gopcms.file.converter.cache-root} 디렉터리에 {@code {sha256}.pdf} 형태로
 * 캐시되어, 같은 파일 재요청 시 즉시 반환 (LibreOffice 호출 0회).
 */
public interface DocumentConvertService {

    /**
     * 원본 파일을 PDF 로 변환하고 캐시된 결과 경로 반환.
     *
     * <p>캐시 hit 시 즉시 반환. 캐시 miss 시 LibreOffice 변환 후 캐시 저장 후 반환.
     * 변환 실패(LibreOffice 미설치/timeout/지원 불가 형식 등) 시 {@link RuntimeException} 던짐.
     *
     * @param sourcePath  원본 파일의 절대 경로 (FileStorage.resolveStoredPath 결과)
     * @param fileHash    SHA-256 hash — 캐시 키 (lowercase 64자)
     * @return 캐시된 PDF 의 절대 경로
     */
    Path ensurePdf(Path sourcePath, String fileHash);
}
