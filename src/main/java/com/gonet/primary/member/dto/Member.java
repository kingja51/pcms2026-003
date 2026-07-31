package com.gonet.primary.member.dto;

import com.gonet.common.base.BaseEntity;
import com.gonet.common.base.SoftDeletable;
import com.gonet.common.crypto.Encrypt;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * tb_member 1건 — 활성 회원 엔티티.
 *
 * <p>PII 필드({@link Encrypt}): email / phone / birthDate / di / parentDi / address / addressDetail.
 * <p>평문 보관: memberName · parentName · nickname — UNIQUE 인덱스 대상 및 운영 조회 편의.
 * <p>중복확인용 hash: emailHash / phoneHash / diHash / parentDiHash — 서비스 계층에서 HMAC 세팅.
 *
 * <p>식별 정책 (2026-05-02 변경): 회원 식별 키는 NICE DI(64byte) 만 사용. CI(88byte) 는 주민번호
 * 동등 강도 PII 로 취급하여 시스템 어디에도 적재하지 않음. tb_member.ci → di 로 컬럼명 변경됨
 * (DDL 요청서 2026-05-02_member_ci_to_di_rename.sql 참조).
 */
@Getter
@Setter
public class Member extends BaseEntity implements SoftDeletable {

    private String        memberId;
    private Long          memberSeq;
    private String        siteId;
    private String        loginId;
    private String        password;              // BCrypt
    private LocalDateTime passwordChangedAt;
    private LocalDateTime passwordExpireAt;

    private String        roleIds;               // CSV (계층 확장)
    private String        groupIds;              // CSV (등급/혜택)

    private String          memberName;           // 평문 VARCHAR(150) — UNIQUE 인덱스 일부
    private String          nickname;
    @Encrypt private String email;
    private String          emailHash;
    private String          emailVerifiedYn;
    @Encrypt private String phone;
    private String          phoneHash;
    @Encrypt private String birthDate;
    /**
     * 출생 연도 4자리 평문 — 연령대 통계용 (PIPA 일반 개인정보, 암호화 의무 X).
     * 가입/수정 시 Service 가 birthDate 의 YYYY 부분 자동 추출. 사용자 직접 입력 X.
     * 통계 시점에 {@code FLOOR((YEAR(NOW()) - birth_year::int) / 10) * 10} 으로 연령대 계산.
     */
    private String          birthYear;
    private String          gender;               // M/F/N

    @Encrypt private String di;
    private String          diHash;

    // 14세 미만 회원 법정대리인(부모) 정보. 일반 회원은 모두 NULL.
    private String          parentName;           // 평문 VARCHAR(150)
    @Encrypt private String parentDi;
    private String          parentDiHash;         // HMAC-SHA256(parentDi)

    private String          addressZipcode;
    @Encrypt private String address;
    @Encrypt private String addressDetail;

    private String          joinType;             // EMAIL/KAKAO/...
    private String          status;               // ACTIVE/LOCKED/EMAIL_PENDING/SUSPENDED
    private Integer         loginFailCount;
    private LocalDateTime   lockedUntil;
    private LocalDateTime   lastLoginAt;
    private String          lastLoginIp;
    private LocalDateTime   lastAccessAt;
    private LocalDateTime   dormantScheduledAt;

    private String          privacyAgreeYn;
    private String          termsAgreeYn;
    private String          marketingAgreeYn;
    private String          smsAgreeYn;
    private String          emailAgreeYn;

    private String          deleteYn;

    // JOIN 표시용
    private String          siteCode;
    private String          siteName;

    /**
     * tb_member_dormant 조회 시에만 채워지는 휴면 전환 일시.
     * tb_member 에는 dormant_at 컬럼이 없음 — 휴면 도메인 mapper(d.dormant_at) 가
     * 본 DTO 를 재사용해 채운다. tb_member 일반 조회 시에는 null.
     */
    private LocalDateTime   dormantAt;
}
