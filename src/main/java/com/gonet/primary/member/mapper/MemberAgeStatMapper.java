package com.gonet.primary.member.mapper;

import com.gonet.primary.member.dto.AgeGroupStat;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 회원 연령대 통계.
 *
 * <p>연령대 = {@code FLOOR((current_year - birth_year) / 10) * 10}.
 * birth_year NULL 은 별도 "미입력" 분류로 반환.
 *
 * <p>활성 회원 (tb_member, delete_yn='N') + 휴면 회원 (tb_member_dormant) 모두 집계 가능.
 * 일반적 통계는 활성 회원만.
 */
@EgovMapper
public interface MemberAgeStatMapper {

    /** 활성 회원 연령대 분포 (사이트 필터 옵션). */
    List<AgeGroupStat> activeByAgeGroup(@Param("siteId") String siteId);

    /** 활성 + 휴면 합산 연령대 분포. */
    List<AgeGroupStat> activeAndDormantByAgeGroup(@Param("siteId") String siteId);
}
