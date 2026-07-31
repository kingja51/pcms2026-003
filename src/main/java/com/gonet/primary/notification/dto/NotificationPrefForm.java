package com.gonet.primary.notification.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * 회원/관리자 채널 토글 폼.
 *
 * <p>{@code notificationType='ALL'} 이면 사용자 단위 default 변경. 타입별 상세 변경은
 * 같은 폼에 type 만 다르게 채워서 POST.
 */
@Getter
@Setter
public class NotificationPrefForm {

    private String userId;          // 관리자 강제 변경 시만 채움. 회원 본인 변경은 Service 가 강제 주입

    @NotBlank
    private String notificationType = "ALL";

    /** 체크박스 미체크 시 'N', 체크 시 'Y'. 폼 미전달 시 'N' 으로 처리. */
    private String channelInappYn  = "Y";
    private String channelEmailYn  = "Y";
}
