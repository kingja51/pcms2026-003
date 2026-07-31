package com.gonet.primary.schedule.dto;

import com.gonet.common.base.BaseEntity;
import com.gonet.common.base.SoftDeletable;
import com.gonet.common.base.UseFlagged;
import lombok.Getter;
import lombok.Setter;

/**
 * tb_schedule_master 엔티티 — 일정 마스터 (얇은 owner).
 *
 * <p>역할: 사이트/메뉴 컨텍스트 + 일정 그룹 헤더. 실제 일정 데이터(시간/장소/색상 등) 는
 * {@link Schedule} (1:N detail) 에 보관.
 */
@Getter
@Setter
public class ScheduleMaster extends BaseEntity implements SoftDeletable, UseFlagged {

    private String scheduleMasterId;
    private String siteId;
    private String menuId;
    private String masterTitle;
    private String masterContent;
    private String useYn;
    private String deleteYn;

    // tb_site JOIN 으로 채워지는 노출 필드 — INSERT/UPDATE 대상 아님
    private String siteCode;
    private String siteName;
    private String layoutPath;
}
