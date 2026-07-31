package com.gonet.primary.board.like.service;

import com.gonet.common.audit.AuditEvent;
import com.gonet.common.audit.AuditLogger;
import com.gonet.common.util.JsonUtils;
import com.gonet.common.util.UuidV7Generator;
import com.gonet.config.datasource.PrimaryDataSourceConfig;
import com.gonet.primary.board.article.mapper.BbsArticleMapper;
import com.gonet.primary.board.comment.mapper.BbsCommentMapper;
import com.gonet.primary.board.like.dto.BbsLike;
import com.gonet.primary.board.like.dto.BbsLikeTarget;
import com.gonet.primary.board.like.dto.LikeToggleResult;
import com.gonet.primary.board.like.mapper.BbsLikeMapper;
import com.gonet.primary.system.login.dto.CustomUserDetails;
import org.egovframe.rte.fdl.cmmn.EgovAbstractServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 게시판 좋아요 서비스 — article + comment 통합 토글.
 *
 * <p>설계:
 * <ul>
 *   <li>UNIQUE (target_type, target_id, user_id) — DB 가 1인 1회 보장</li>
 *   <li>토글: 활성 행이 있으면 OFF (delete_yn='Y'), 비활성 행이 있으면 ON (delete_yn='N'),
 *       둘 다 없으면 INSERT</li>
 *   <li>토글 후 대상의 like_count 비정규화 컬럼 동기화 (전체 COUNT 로 재계산)</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true, transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
public class BoardLikeServiceImpl extends EgovAbstractServiceImpl implements BoardLikeService {

    private static final Logger log = LoggerFactory.getLogger(BoardLikeServiceImpl.class);

    private final BbsLikeMapper    likeMapper;
    private final BbsArticleMapper articleMapper;
    private final BbsCommentMapper commentMapper;
    private final AuditLogger      auditLogger;

    public BoardLikeServiceImpl(BbsLikeMapper likeMapper,
                                 BbsArticleMapper articleMapper,
                                 BbsCommentMapper commentMapper,
                                 AuditLogger auditLogger) {
        this.likeMapper    = likeMapper;
        this.articleMapper = articleMapper;
        this.commentMapper = commentMapper;
        this.auditLogger   = auditLogger;
    }

    // -------------------------------------------------------------------
    // 토글
    // -------------------------------------------------------------------

    @Override
    @Transactional(transactionManager = PrimaryDataSourceConfig.TRANSACTION_MGR)
    public LikeToggleResult toggle(BbsLikeTarget target, String targetId, String sourceUrl, CustomUserDetails me) {
        if (target == null) {
            throw new IllegalArgumentException("좋아요 대상 종류가 올바르지 않습니다.");
        }
        if (targetId == null || targetId.isBlank()) {
            throw new IllegalArgumentException("좋아요 대상 ID 가 비어 있습니다.");
        }
        if (me == null || me.getUserId() == null) {
            throw new AccessDeniedException("좋아요는 로그인 후 이용 가능합니다.");
        }

        String type   = target.name();
        String userId = me.getUserId();

        BbsLike active = likeMapper.findActive(type, targetId, userId);
        boolean nowLiked;
        int delta;
        if (active != null) {
            // 토글 OFF
            likeMapper.markInactive(active.getLikeId());
            nowLiked = false;
            delta    = -1;
            log.info("===BBS_LIKE_OFF target={}/{} user={}", type, targetId, userId);
        } else {
            BbsLike inactive = likeMapper.findInactive(type, targetId, userId);
            if (inactive != null) {
                likeMapper.markActive(inactive.getLikeId());
            } else {
                BbsLike fresh = new BbsLike();
                fresh.setLikeId(UuidV7Generator.generate("BLK"));
                fresh.setTargetType(type);
                fresh.setTargetId(targetId);
                fresh.setUserId(userId);
                fresh.setUserType(safeUpper(me.getUserType(), "MEMBER"));
                fresh.setSourceUrl(trimUrl(sourceUrl));
                fresh.setDeleteYn("N");
                likeMapper.insert(fresh);
            }
            nowLiked = true;
            delta    = +1;
            log.info("===BBS_LIKE_ON target={}/{} user={} src={}", type, targetId, userId, trimUrl(sourceUrl));
        }

        // H-1 race 가드 — DB 단 원자적 증감. SELECT→UPDATE 사이의 race window 차단.
        incrementCount(target, targetId, delta);
        // 응답/감사 표시용 — 가장 최신 카운트 (race 가 있어도 표시 일관성 우선).
        long count = likeMapper.countActive(type, targetId);

        auditLogger.write(AuditEvent.of(
                nowLiked ? "BBS_LIKE_ON" : "BBS_LIKE_OFF",
                auditEntityName(target))
            .withTarget(targetId)
            .withAfter("{\"liked\":" + nowLiked + ",\"likeCount\":" + count + "}")
            .withResult("SUCCESS"));

        return new LikeToggleResult(nowLiked, count);
    }

    @Override
    public boolean isLikedBy(BbsLikeTarget target, String targetId, CustomUserDetails me) {
        if (target == null || targetId == null || targetId.isBlank()) return false;
        if (me == null || me.getUserId() == null) return false;
        return likeMapper.findActive(target.name(), targetId, me.getUserId()) != null;
    }

    // -------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------

    /**
     * H-1 race 가드 — 원자적 증감 (+1/-1). DB 의 단일 UPDATE 로 처리.
     * CONTENT 는 비정규화 컬럼 없음 → no-op (countActive 로 즉시 조회).
     */
    private void incrementCount(BbsLikeTarget target, String targetId, int delta) {
        if (target == BbsLikeTarget.ARTICLE) {
            articleMapper.incrementLikeCount(targetId, delta);
        } else if (target == BbsLikeTarget.COMMENT) {
            commentMapper.incrementLikeCount(targetId, delta);
        }
    }

    private static String safeUpper(String s, String fallback) {
        return (s == null || s.isBlank()) ? fallback : s.trim().toUpperCase();
    }

    private static String auditEntityName(BbsLikeTarget target) {
        return switch (target) {
            case ARTICLE -> "tb_bbs_article";
            case COMMENT -> "tb_bbs_comment";
            case CONTENT -> "tb_content";
        };
    }

    /** source_url 컬럼 폭(1000) 보호 + 빈 값 정규화. */
    private static String trimUrl(String url) {
        if (url == null) return null;
        String t = url.trim();
        if (t.isEmpty()) return null;
        return t.length() > 1000 ? t.substring(0, 1000) : t;
    }
}
