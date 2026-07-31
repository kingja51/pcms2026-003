package com.gonet.primary.board.master.retention;

import com.gonet.common.retention.RetentionBucket;
import com.gonet.common.retention.RetentionTarget;
import com.gonet.primary.board.master.mapper.BbsMasterMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * tb_bbs_master soft-delete retention target.
 *
 * <p>FK 종속성 — article(20)/category(15) 의 부모. 게시판 자체 폐기는 드물지만 운영자
 * 자유게시판 통폐합 등에서 발생. childOrder=40 으로 모든 자식 정리 완료 후 실행.
 * <p>bucket = DAYS_90 — 운영 의사결정 시간 여유 (오삭제 복구 창구). 일반 게시글(DAYS_30) 보다 길게.
 */
@Component
public class BbsMasterRetentionTarget implements RetentionTarget {

    private final BbsMasterMapper mapper;

    public BbsMasterRetentionTarget(BbsMasterMapper mapper) {
        this.mapper = mapper;
    }

    @Override public String tableName()              { return "tb_bbs_master"; }
    @Override public RetentionBucket defaultBucket() { return RetentionBucket.DAYS_90; }
    @Override public int childOrder()                { return 40; }

    @Override
    public int purge(LocalDateTime cutoff) {
        return mapper.purgeSoftDeletedOlderThan(cutoff);
    }

    @Override
    public int countCandidates(LocalDateTime cutoff) {
        return mapper.countSoftDeletedOlderThan(cutoff);
    }
}
