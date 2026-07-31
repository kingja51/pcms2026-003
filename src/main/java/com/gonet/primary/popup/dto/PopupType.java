package com.gonet.primary.popup.dto;

import java.util.Locale;

/**
 * tb_popup.popup_type 도메인 enum.
 *
 * <ul>
 *   <li>{@link #LAYER}  : 페이지 안 레이어 — 화면 위 absolute 박스</li>
 *   <li>{@link #MODAL}  : 화면 중앙 모달 — 배경 dim + 닫기 강제 인지</li>
 *   <li>{@link #WINDOW} : 별창 (window.open) — 차단되면 LAYER 로 폴백</li>
 *   <li>{@link #BANNER} : 헤더/사이드 인라인 — 닫지 않아도 OK</li>
 * </ul>
 */
public enum PopupType {
    LAYER, MODAL, WINDOW, BANNER;

    public static PopupType safeParse(String raw) {
        if (raw == null || raw.isBlank()) return LAYER;
        try {
            return PopupType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return LAYER;
        }
    }
}
