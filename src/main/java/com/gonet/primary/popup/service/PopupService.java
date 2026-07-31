package com.gonet.primary.popup.service;

import com.gonet.primary.popup.dto.Popup;
import com.gonet.primary.popup.dto.PopupSaveForm;
import com.gonet.primary.popup.dto.PopupSearch;

import java.util.List;

/**
 * 팝업 서비스 — 관리자 CRUD + 사용자 활성 팝업 조회.
 *
 * <p>활성 팝업 조회({@link #findActive}) 는 매 페이지 요청마다 호출되므로 가볍게 유지.
 * 캐싱은 향후 강화 (BULK 이벤트로 evict).
 */
public interface PopupService {

    List<Popup> search(PopupSearch search);

    int count(PopupSearch search);

    Popup get(String popupId);

    /**
     * 사이트별 활성 팝업 — DB 의 use_yn / show_from~show_to 기본 필터에 더해
     * 요일(show_days) / 시간대(show_time_from~show_time_to) 까지 Service 단에서 평가.
     */
    List<Popup> findActive(String siteId);

    String create(PopupSaveForm form);

    void update(PopupSaveForm form);

    void toggleUse(String popupId, boolean active);

    void softDelete(String popupId);
}
