package com.gonet.primary.system.site.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Objects;

/**
 * SiteContext 구성요소(사이트/템플릿/메뉴) 가 변경됐음을 알리는 이벤트.
 *
 * <p>발행 지점 (관리자 MngController 의 CUD 이후):
 * <ul>
 *   <li>사이트 수정 (tb_site: template_id 변경, use_yn 등)</li>
 *   <li>템플릿 수정/삭제 (tb_template)</li>
 *   <li>메뉴 CUD (tb_menu)</li>
 * </ul>
 *
 * <p>{@code siteId == null} 이면 전체 캐시 무효화 (예: 다수 사이트 일괄 변경).
 */
@Getter
@RequiredArgsConstructor
public final class SiteContextChangedEvent {

    public enum Reason { SITE_UPDATED, TEMPLATE_CHANGED, MENU_CHANGED, BULK }

    private final String siteId;
    private final Reason reason;

    public boolean isBulk() {
        return reason == Reason.BULK || siteId == null;
    }

    /** {@link Objects#requireNonNull} 호환을 위해 명시적 생성자 (Lombok @RequiredArgsConstructor 가 검증 미수행). */
    public static SiteContextChangedEvent of(String siteId, Reason reason) {
        return new SiteContextChangedEvent(siteId, Objects.requireNonNull(reason));
    }
}
