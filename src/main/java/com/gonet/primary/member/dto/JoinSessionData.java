package com.gonet.primary.member.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 회원가입 7단계 플로우 — 1~4 단계에서 누적한 값을 세션에 보관하는 용기.
 *
 * <p>세션 키: {@link #SESS_KEY}. 가입 완료(Step 6) 또는 유형/약관 재시작 시 제거.
 * <p>본인인증(NICE) 콜백이 name·phone·di 를 채운 뒤 폼 prefill 에 사용된다.
 *
 * <p>식별 정책 (2026-05-02): 본 시스템은 NICE DI(64byte) 만 회원 식별 키로 사용. CI 는 PII
 * 강도 사유로 적재하지 않음.
 *
 * <p><b>14세 미만 규약</b>: 본인인증은 <em>부모</em>가 수행. 따라서 CHILD 모드의 세션 DI 는
 * 부모 DI 로 해석되어 {@code parent_name/parent_di/parent_di_hash} 로 매핑되고, 자녀의
 * {@code di/di_hash} 에도 동일 값이 저장된다(사용자 정책).
 */
@Getter
@Setter
public class JoinSessionData {

    /** HttpSession 속성 키 — 일관 관리 목적 상수. */
    public static final String SESS_KEY = "PCMS_JOIN_SESSION";

    /** 'ADULT' (일반) / 'CHILD' (14세 미만). */
    private String userType;

    /** 약관 동의 완료 표시 — Step 2 에서 true. */
    private boolean agreed;

    /** 본인인증 완료 표시 — NICE 콜백 흡수 시 true. */
    private boolean diVerified;

    /** 인증된 본인(성인) 또는 부모(14세 미만) 의 실명. */
    private String verifiedName;

    /** 인증 휴대폰 번호 (010-1234-5678 형식). */
    private String verifiedPhone;

    /** 인증 DI 평문 — {@code @Encrypt} 대상으로 저장 시 서비스 계층이 암호화. */
    private String verifiedDi;

    /** DI HMAC-SHA256 해시 — {@code di_hash}/{@code parent_di_hash} 중복확인용. */
    private String verifiedDiHash;


    /** 인증 생년월일. */
    private String verifiedBirthday;

    private String siteCode;

    /**
     * 가입 경로 코드 — {@code tb_member.join_type} 직접 저장.
     * <ul>
     *   <li>OAuth2 가입 시 컨트롤러가 provider 명({@code KAKAO/NAVER/GOOGLE/APPLE}) 명시</li>
     *   <li>NICE 홈페이지 가입은 비워두고 Service 가 userAgent 로 {@code HOMEPAGE}/{@code MOBILE} 자동 결정</li>
     * </ul>
     */
    private String joinType;

    // ====== Step 2 약관 동의 결과 — Step 4 폼에서 hidden 으로 재주입 ======
    /** "Y" — 이용약관 동의 (필수). */
    private String termsAgreeYn;
    /** "Y" — 개인정보 수집·이용 동의 (필수). */
    private String privacyAgreeYn;
    /** "Y"/"N" — 마케팅 수신 동의 (선택). */
    private String marketingAgreeYn = "N";
    /** "Y"/"N" — SMS 수신 동의 (선택). */
    private String smsAgreeYn = "N";
    /** "Y"/"N" — 이메일 수신 동의 (선택). */
    private String emailAgreeYn = "N";


    /** ADULT 여부 단축 헬퍼. */
    public boolean isAdult() { return "ADULT".equals(userType); }
    /** CHILD 여부 단축 헬퍼. */
    public boolean isChild() { return "CHILD".equals(userType); }
}
