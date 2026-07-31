package com.gonet.primary.member.dormant.service;

import com.gonet.primary.member.dto.Member;

/**
 * 회원 휴면 라이프사이클 — 알림 발송 · 전환 · 복원.
 *
 * <p>스케줄러({@code DormantScheduler}) 가 주기적으로 호출. 복원은 사용자 폼
 * ({@code DormantRestoreUsrController}) 에서 호출.
 */
public interface DormantService {

    /**
     * 주기 배치 진입점 — 매일 01:00 cron 이 호출.
     * <ol>
     *   <li>30일/7일/1일 전 알림 발송</li>
     *   <li>1년 경과 계정 휴면 전환 + 전환 완료 메일</li>
     * </ol>
     */
    void runDaily();

    /** 강제 진입점 — 관리자 수동 트리거 / 통합 테스트 용도. */
    DormantBatchResult runDailyOnce();

    /**
     * <b>본인확인이 끝난 뒤</b>의 역이관 — {@code tb_member_dormant} → {@code tb_member}.
     *
     * <p><b>이 메서드는 본인확인을 하지 않는다.</b> 확인은 호출자
     * ({@code DormantRestoreService} 의 실명인증 / 이메일 OTP 경로)가 마치고 들어온다.
     * 그래서 아무 memberId 로나 부르면 그대로 해제된다 — 반드시 확인 뒤에만 부를 것.
     *
     * <p>001 은 여기서 <b>로그인ID + 이름 + 이메일 + 비밀번호 3요소 일치</b>로 확인까지
     * 함께 했다({@code restoreWithCredentials}). 003 은 그 방식을 <b>쓰지 않는다</b> —
     * 휴면 계정의 비밀번호는 사용자가 가장 잊기 쉬운 정보인데, 그걸 요구하면
     * 정작 본인이 못 푼다. 확인 수단을 실명인증 / 이메일 OTP 택1 로 바꾸고
     * (개발가이드 §10-6) 확인과 역이관을 분리했다.
     *
     * @return 복원된 회원 정보 (후속 로그인 유도용). 대상이 없으면 {@code null}
     */
    Member restoreVerified(String memberId);

    /** 배치 1회 실행 결과 요약 — 테스트·관리자 화면에서 활용 가능. */
    record DormantBatchResult(int notified30D,
                               int notified7D,
                               int notified1D,
                               int transferred,
                               int purged,
                               int mailFailures) {}
}
