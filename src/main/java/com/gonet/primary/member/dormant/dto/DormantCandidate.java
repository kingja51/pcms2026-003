package com.gonet.primary.member.dormant.dto;

import com.gonet.common.crypto.Encrypt;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 휴면 전환 알림·전환 배치가 처리할 후보 행 (경량 조회).
 *
 * <p>전체 {@link com.gonet.primary.member.dto.Member} 엔티티를 로드하지 않고
 * 배치에 필요한 필드만 (이름·이메일·loginId·lastLoginAt) 가져와 메일 발송에 사용.
 * <p>{@code memberName} / {@code email} 은 {@link Encrypt} 부착 — 자동 복호화.
 */
@Getter
@Setter
public class DormantCandidate {
    private String        memberId;
    private String        loginId;
    @Encrypt private String memberName;
    @Encrypt private String email;
    private LocalDateTime lastLoginAt;
}
