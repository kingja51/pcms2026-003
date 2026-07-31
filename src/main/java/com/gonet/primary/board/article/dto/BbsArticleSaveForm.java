package com.gonet.primary.board.article.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 게시글 등록/수정 폼.
 *
 * <p>입력:
 * <ul>
 *   <li>{@code title} — 1~300자, 필수</li>
 *   <li>{@code content} — 본문 (MEDIUMTEXT 한도 16MB, 폼은 1MB 권장)</li>
 *   <li>{@code writerName} — 비로그인일 때 필수, 로그인 시 서버가 사용자 표시명으로 덮어씀</li>
 *   <li>{@code secretYn} — 'Y' 면 비밀글 (작성자/관리자만 본문 조회)</li>
 *   <li>{@code noticeYn} — 'Y' 면 공지 (관리자만 토글 가능; 일반 사용자가 보내도 Service 가 무시)</li>
 * </ul>
 *
 * <p>{@code articleId} 는 수정 시에만 hidden 으로 제출.
 * {@code bbsMasterId} 는 작성 진입 URL 에서 결정 — 폼에서는 hidden 으로만 보존.
 */
@Getter
@Setter
public class BbsArticleSaveForm {

    private String articleId;

    @NotBlank
    @Size(max = 40)
    private String bbsMasterId;

    @Size(max = 40)
    private String categoryId;

    @NotBlank
    @Size(min = 1, max = 300)
    private String title;

    @NotBlank
    @Size(max = 1_048_576, message = "본문은 1MB 이내")
    private String content;

    @Size(max = 100)
    private String writerName;

    private String secretYn;
    private String noticeYn;

    /** BODO — 언론사명. 다른 도메인은 비워둠. */
    @Size(max = 100)
    private String pressName;

    /** BODO — 외부 기사 URL, YOUTUBE — 영상 URL. */
    @Size(max = 500)
    private String linkUrl;

    /** 게시 일자 — yyyy-MM-dd 문자열. 폼 제출 시 빈 문자열은 null 로 폴백. */
    @Size(max = 10)
    private String publishedAt;

    /**
     * 파일첨부 fragment 가 hidden input 으로 보내는 fileId JSON 배열.
     * 예: {@code ["019d1234-...","019d5678-..."]}.
     * 화면에서 사용자가 X 버튼으로 빼면 그 fileId 는 배열에서 빠지고,
     * Service 가 group 내 다른 파일을 soft delete 한다.
     */
    @Size(max = 16_384, message = "attachments 길이 초과")
    private String attachmentsJson;

    public static String yn(String raw) {
        return ("Y".equalsIgnoreCase(raw)) ? "Y" : "N";
    }
}
