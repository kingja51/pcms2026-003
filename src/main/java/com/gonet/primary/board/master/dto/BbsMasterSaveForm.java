package com.gonet.primary.board.master.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 게시판 마스터 등록/수정 폼.
 *
 * <p>입력 단위:
 * <ul>
 *   <li>{@code fileSizeMaxMb} — 사용자에게는 MB 로 받아 서버에서 byte 로 환산</li>
 *   <li>{@code bbsCode} — 사이트 내 UNIQUE, 영문/숫자/하이픈/언더스코어만, 첫 글자는 영문</li>
 *   <li>{@code bbsType} — {@link BbsType} 열거 (NOTICE/BODO/FREE/FAQ/QNA/GALLERY/FILE)</li>
 * </ul>
 *
 * <p>{@code Yn} 계열 필드는 체크박스 미체크 시 null 로 들어오므로 Service 에서 'N' 폴백.
 */
@Getter
@Setter
public class BbsMasterSaveForm {

    private String bbsMasterId;

    @NotBlank
    @Size(max = 40)
    private String siteId;

    @NotBlank
    @Size(min = 2, max = 50)
    @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_\\-]*$",
             message = "bbs_code 는 영문으로 시작, 영문/숫자/하이픈/언더스코어만 사용")
    private String bbsCode;

    @NotBlank
    @Size(max = 100)
    private String bbsName;

    @NotBlank
    @Pattern(regexp = "^(NOTICE|BODO|FREE|FAQ|QNA|GALLERY|FILE|PDF|YOUTUBE)$",
             message = "bbs_type 는 NOTICE/BODO/FREE/FAQ/QNA/GALLERY/FILE/PDF/YOUTUBE 중 하나")
    private String bbsType;

    private String commentYn;
    private String fileYn;
    /** HTML 본문 사용 여부 — 체크박스. null/미체크 시 'N'. */
    private String htmlYn;
    /** CAPTCHA 사용 여부 — 체크박스. null/미체크 시 'N'. Y 이면 글쓰기 시 reCAPTCHA v3 강제. */
    private String captchaYn;

    @Min(value = 0, message = "첨부 최대 개수는 0 이상")
    private int fileCountMax = 5;

    @Min(value = 1, message = "첨부 최대 크기(MB) 는 1 이상")
    private int fileSizeMaxMb = 10;

    private String anonymousYn;
    private String noticeTopYn;

    @Pattern(regexp = "^(ALL|MEMBER|EMPLOYEE|ADMIN)$",
             message = "read_auth 는 ALL/MEMBER/EMPLOYEE/ADMIN 중 하나")
    private String readAuth = "ALL";

    @Pattern(regexp = "^(MEMBER|EMPLOYEE|ADMIN)$",
             message = "write_auth 는 MEMBER/EMPLOYEE/ADMIN 중 하나 (비로그인 작성 미지원)")
    private String writeAuth = "MEMBER";

    /**
     * 첨부 다운로드 권한 (게시판 단위 일괄). read_auth 와 별개.
     * <ul>
     *   <li>ANONYMOUS — 누구나 (비회원 포함) — 기본값 (2026-07-07 ROLE_MEMBER 에서 변경,
     *       대학 사이트는 교육과정 등 공개 자료가 다수라 비회원 열람이 기본)</li>
     *   <li>ROLE_MEMBER — 회원 이상</li>
     *   <li>ROLE_EMPLOYEE — 직원 이상</li>
     *   <li>ROLE_STAFF — STAFF 이상</li>
     *   <li>OWNER_PRIVACY — 본인(created_by) + ROLE_PRIVACY 보유자만</li>
     *   <li>ROLE_MANAGER — 책임자 이상</li>
     *   <li>ROLE_ADMIN — 전체관리자</li>
     * </ul>
     */
    @NotBlank
    @Pattern(regexp = "^(ANONYMOUS|ROLE_MEMBER|ROLE_EMPLOYEE|ROLE_STAFF|OWNER_PRIVACY|ROLE_MANAGER|ROLE_ADMIN)$",
             message = "download_auth 값이 올바르지 않습니다.")
    private String downloadAuth = "ANONYMOUS";

    private String useYn;

    @Size(max = 2000)
    private String description;

    /**
     * 통합 게시판(전체글 보기) 대상 bbs_master_id 의 CSV.
     * <ul>
     *   <li>NULL/빈문자 — 일반 게시판 (자기 글 CRUD)</li>
     *   <li>"id1,id2,id3" — 통합 게시판. 대상 게시판 글을 list/count 에서만 합쳐 보여준다.
     *       작성/수정/삭제는 Service 단에서 거부됨.</li>
     * </ul>
     * <p>UUID v7 40자 + 콤마 = 41자, 1000/41 ≈ 최대 23개 (DB 길이 제약, 본인 포함이라서 1개 제외).
     */
    @Size(max = 1000, message = "통합 게시판 대상은 최대 1000자(약 23개)까지 지정 가능합니다.")
    private String groupedBoardIds;

    /** 폼이 평소대로 'Y' 또는 null 만 보낸다는 가정. null/blank → 'N' 으로 정규화. */
    public static String yn(String raw) {
        return ("Y".equalsIgnoreCase(raw)) ? "Y" : "N";
    }
}
