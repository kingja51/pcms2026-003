package com.gonet.primary.file.dto;

/**
 * ClamAV 검사 상태 — {@code tb_file.virus_scan_status}.
 *
 * <p>전이 규칙:
 * <pre>
 *   PENDING ─(ClamAV clean)─▶ CLEAN      ← 사용자 다운로드 허용
 *   PENDING ─(ClamAV virus)─▶ INFECTED   ← 격리 유지, 삭제 대상
 *   PENDING ─(timeout/err)──▶ ERROR      ← 관리자 검토 필요
 * </pre>
 */
public enum VirusScanStatus {

    PENDING,
    CLEAN,
    INFECTED,
    ERROR;

    public static VirusScanStatus safeParse(String raw) {
        if (raw == null || raw.isBlank()) return PENDING;
        try {
            return VirusScanStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return ERROR;
        }
    }

    /**
     * 사용자 다운로드 허용 여부 — INFECTED / ERROR 모두 차단.
     * <ul>
     *   <li>{@code CLEAN}    : 허용 — ClamAV 안전 판정</li>
     *   <li>{@code PENDING}  : 허용 — 스캔 대기 / NoOp 모드 (clamav.enabled=false). 6중 방어 스택을
     *       통과해 격리 저장소에 안착한 파일이므로 사용자 다운로드 허용</li>
     *   <li>{@code INFECTED} : 차단 — 바이러스 검출. 관리자 강제 다운로드(adminDownload) 만 가능</li>
     *   <li>{@code ERROR}    : 차단 — 스캔 인프라 장애로 결과 미상. 관리자 검토 후 재스캔 필요</li>
     * </ul>
     */
    public boolean isDownloadable() {
        return this == CLEAN || this == PENDING;
    }
}
