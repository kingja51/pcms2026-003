package com.gonet.primary.board.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 댓글 작성/수정 폼.
 *
 * <p>입력:
 * <ul>
 *   <li>{@code content} — 1~4000자, 필수</li>
 *   <li>{@code parentCommentId} — 대댓글 시 부모 ID (선택)</li>
 *   <li>{@code commentId} — 수정 시에만 사용 (hidden)</li>
 * </ul>
 */
@Getter
@Setter
public class BbsCommentSaveForm {

    private String commentId;

    @NotBlank
    @Size(max = 40)
    private String articleId;

    @Size(max = 40)
    private String parentCommentId;

    @NotBlank
    @Size(min = 1, max = 4000, message = "댓글은 1~4000자")
    private String content;

    /** 비밀 댓글 — 'Y' 또는 null/'N'. 체크박스 미체크 시 파라미터 미전송 → null. */
    private String secretYn;
}
