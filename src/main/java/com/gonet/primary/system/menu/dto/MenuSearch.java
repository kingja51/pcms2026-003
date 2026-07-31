package com.gonet.primary.system.menu.dto;

import com.gonet.common.dto.PageRequest;
import lombok.Getter;
import lombok.Setter;

/**
 * 메뉴 검색 조건 — {@link PageRequest} 상속.
 * <p>관리자 화면은 기본적으로 트리 전체 표시 (페이징 없음),
 * 검색어 입력 시 평탄화된 flat 결과 반환.
 */
@Getter
@Setter
public class MenuSearch extends PageRequest {

    private String siteId;
    private String useYn;
    private String menuType;
}
