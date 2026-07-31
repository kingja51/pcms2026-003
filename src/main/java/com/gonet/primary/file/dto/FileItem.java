package com.gonet.primary.file.dto;

import com.gonet.common.base.BaseEntity;
import com.gonet.common.base.SoftDeletable;
import lombok.Getter;
import lombok.Setter;

/**
 * tb_file 1건 — 관리자 조회/CRUD 풀 DTO.
 *
 * <p>6중 방어 스택 매핑:
 * <ul>
 *   <li>{@code extension}          — 1) 확장자 화이트리스트 통과값</li>
 *   <li>{@code mimeDetected}       — 2) Tika 매직바이트 결과 (클라이언트 헤더와 분리)</li>
 *   <li>{@code reencodedYn}        — 3) Thumbnailator 재인코딩 성공 여부</li>
 *   <li>{@code fileHash}           — 4) SHA-256 FIM</li>
 *   <li>{@code storedPath}         — 5) 격리 저장소 → 정식 root 이동 결과</li>
 *   <li>{@code virusScanStatus}    — 6) ClamAV 검사 상태</li>
 * </ul>
 *
 * <p>파일 이름은 {@code JavaFile} 과의 혼동을 피해 {@code FileItem} 으로 명명.
 */
@Getter
@Setter
public class FileItem extends BaseEntity implements SoftDeletable {

    private String fileId;
    private String fileGroupId;
    private String originalName;
    private String storedName;
    private String storedPath;
    private String thumbnailPath;
    private String extension;
    private String mimeDetected;
    private String mimeClient;
    private long   sizeBytes;
    private String fileHash;
    private String isImageYn;
    private String reencodedYn;
    private String virusScanStatus;
    private long   downloadCount;
    private int    sortOrder;
    private String deleteYn;

    private String entityType;
    private String entityId;
    private String siteCode;

    public VirusScanStatus scanStatusEnum() {
        return VirusScanStatus.safeParse(virusScanStatus);
    }

    public boolean isImage() {
        return "Y".equalsIgnoreCase(isImageYn);
    }

    public boolean isReencoded() {
        return "Y".equalsIgnoreCase(reencodedYn);
    }
}
