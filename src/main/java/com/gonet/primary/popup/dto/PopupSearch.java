package com.gonet.primary.popup.dto;

import com.gonet.common.dto.PageRequest;
import lombok.Getter;
import lombok.Setter;

/**
 * 팝업 목록 검색 조건.
 *
 * <p>필터:
 * <ul>
 *   <li>{@code siteId}    — 사이트 한정</li>
 *   <li>{@code popupType} — 타입 필터 (LAYER/MODAL/WINDOW/BANNER/ALL)</li>
 *   <li>{@code useYn}     — 사용중지 포함 여부 (ALL 이면 무관)</li>
 *   <li>{@code keyword}   — popup_title LIKE ({@link PageRequest})</li>
 * </ul>
 */
@Getter
@Setter
public class PopupSearch extends PageRequest {

    private String siteId;
    private String popupType;
    private String useYn;
}
