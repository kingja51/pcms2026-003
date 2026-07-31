package com.gonet.primary.member.otp.retention;

import com.gonet.common.retention.RetentionBucket;
import com.gonet.common.retention.RetentionTarget;
import com.gonet.primary.member.otp.mapper.MemberOtpMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * {@code tb_member_otp} 보존 정리 — P5 에서 도입한 OTP 테이블의 청소 담당.
 *
 * <p>P5 에서 매퍼 질의({@code deleteExpiredBefore})만 만들고 <b>호출할 배치가 없어</b>
 * 만료·소비된 행이 무한 누적되는 상태였다(PLAN §7 기록). 여기서 닫는다.
 *
 * <p>새 스케줄러를 만들지 않고 {@link RetentionTarget} SPI 에 붙는 이유:
 * {@code SoftDeleteRetentionScheduler} 가 이미 <b>dry-run · 감사 로그 · ShedLock</b> 을
 * 갖추고 있다. 같은 것을 다시 만들면 dry-run 이 한쪽에만 있는 식으로 갈린다.
 *
 * <p><b>다른 대상과 의미가 조금 다르다.</b> 대부분의 {@code RetentionTarget} 은
 * soft-delete({@code delete_yn='Y'}) 된 행을 지우지만, {@code tb_member_otp} 에는
 * {@code delete_yn} 이 없다. TTL 이 분 단위라 <b>cutoff 보다 오래된 행은 상태와 무관하게
 * 죽은 데이터</b>이므로 {@code created_at} 단일 기준으로 지운다.
 *
 * <p>기본 버킷을 {@link RetentionBucket#DAYS_30} 로 둔 것은 보수적인 선택이다.
 * 실제로는 발급 5분 뒤면 쓸모가 없지만, 남용 조사(누가 언제 몇 번 요청했는지)에
 * 쓸 여지를 조금 남긴다. 평문 코드는 애초에 저장되지 않으므로 오래 두어도
 * 유출 위험이 커지지 않는다.
 */
@Component
public class MemberOtpRetentionTarget implements RetentionTarget {

    private final MemberOtpMapper mapper;

    public MemberOtpRetentionTarget(MemberOtpMapper mapper) {
        this.mapper = mapper;
    }

    @Override public String tableName()              { return "tb_member_otp"; }
    @Override public RetentionBucket defaultBucket() { return RetentionBucket.DAYS_30; }

    /**
     * FK 자식이 없는 독립 테이블이라 순서에 제약이 없다.
     * 다른 대상보다 먼저 돌려 큰 테이블 정리 전에 가볍게 비운다.
     */
    @Override public int childOrder()                { return 10; }

    @Override
    public int purge(LocalDateTime cutoff) {
        return mapper.deleteExpiredBefore(cutoff);
    }

    @Override
    public int countCandidates(LocalDateTime cutoff) {
        return mapper.countExpiredBefore(cutoff);
    }
}
