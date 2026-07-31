package com.gonet.primary.complaint.dto;

import com.gonet.common.base.BaseEntity;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class ComplaintMaster extends BaseEntity {

    private String        complaintMasterId;
    private String        siteId;
    private String        siteCode;
    private String        siteName;
    private String        masterCode;
    private String        masterName;
    private String        description;
    private String        menuId;
    private String        noticeHtml;

    // 작성 인증 정책
    private String        allowMemberYn;
    private String        allowOauth2Yn;
    private String        allowNiceYn;

    /**
     * CAPTCHA 사용 여부 (Y/N). 'Y' 이면 민원 글쓰기 시 reCAPTCHA v3 검증 강제.
     * 민원 도메인은 SNS/NICE 만으로 작성 가능해 봇 차단 필요성이 일반 게시판보다 높음 — 기본 운영 권장 'Y'.
     * 다만 컬럼 기본값은 'N' (관리자가 화면에서 명시 활성화).
     */
    private String        captchaYn;

    // 목록 노출 정책
    private String        showInList;

    // 첨부파일 정책
    private String        fileYn;
    private Integer       fileCountMax;
    private Long          fileSizeMax;

    // 답변 정책
    private String        answerRequiredYn;
    private Integer       answerDeadlineDays;

    // 운영
    private String        useYn;
    private String        deleteYn;

    // 집계 (JOIN)
    private int           articleCount;
}
