package com.gonet.logging.access.mapper;

import com.gonet.logging.access.dto.AccessTopRow;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * log_access + log_file_download 기반 TOP-N 통계 (logging DB).
 *
 * <p>모든 메서드는 {@code from} (inclusive) ~ {@code to} (inclusive) 일 단위 범위 필터.
 * 결과는 {@link AccessTopRow} 로 통일 — 차트가 라벨/값을 똑같이 다룸.
 *
 * <p>본 mapper 는 log_access 를 직접 조회 — 일별 집계(stat_access_*) 로는
 * 표현 불가능한 차원 (client_ip / referer / user_agent / 응답 평균) 을 다룬다.
 * 운영 시 인덱스 부담 가능하므로 호출 빈도와 기간 폭 모니터링 필요.
 */
@EgovMapper
public interface AccessTopMapper {

    /** ① 가장 많이 접속한 IP TOP 20 */
    List<AccessTopRow> topClientIps(@Param("from") LocalDate from,
                                     @Param("to")   LocalDate to,
                                     @Param("limit") int limit);

    /** ② Referer 도메인 TOP 20 — referer URL 에서 호스트 추출 */
    List<AccessTopRow> topRefererDomains(@Param("from") LocalDate from,
                                          @Param("to")   LocalDate to,
                                          @Param("limit") int limit);

    /** ③ 비정상 상태코드(4xx/5xx) 유발 IP TOP 20 */
    List<AccessTopRow> topErrorIps(@Param("from") LocalDate from,
                                    @Param("to")   LocalDate to,
                                    @Param("limit") int limit);

    /**
     * ④ 평균 응답속도가 가장 느린 URI TOP 20 — 최소 호출 횟수(minCalls) 조건.
     * value = 호출 수, valueExtra = 평균 ms.
     */
    List<AccessTopRow> topSlowUris(@Param("from") LocalDate from,
                                    @Param("to")   LocalDate to,
                                    @Param("minCalls") int minCalls,
                                    @Param("limit") int limit);

    /** ⑤ 비로그인(ANONYMOUS) 접근이 가장 많은 URI TOP 20 */
    List<AccessTopRow> topAnonymousUris(@Param("from") LocalDate from,
                                         @Param("to")   LocalDate to,
                                         @Param("limit") int limit);

    /**
     * ⑥ 가장 많이 방문한 메뉴/경로 TOP 20 — log_access 에 menu_id 가 없으므로
     * URI 의 첫 2 경로 세그먼트를 "메뉴" 단위로 그룹핑.
     */
    List<AccessTopRow> topMenuPaths(@Param("from") LocalDate from,
                                     @Param("to")   LocalDate to,
                                     @Param("limit") int limit);

    /** ⑦ 접속량이 가장 많은 User-Agent TOP 20 */
    List<AccessTopRow> topUserAgents(@Param("from") LocalDate from,
                                      @Param("to")   LocalDate to,
                                      @Param("limit") int limit);

    /** ⑧ 가장 많은 종류의 URI 를 스캔(크롤링)한 IP TOP 20 — distinct URI count */
    List<AccessTopRow> topUriScannerIps(@Param("from") LocalDate from,
                                         @Param("to")   LocalDate to,
                                         @Param("limit") int limit);

    /** ⑨ 누적 트래픽(전송량) TOP 20 — bytes_sent SUM */
    List<AccessTopRow> topBandwidthIps(@Param("from") LocalDate from,
                                        @Param("to")   LocalDate to,
                                        @Param("limit") int limit);

    /** ⑩ 404 Not Found 가 가장 많이 발생하는 URI TOP 20 */
    List<AccessTopRow> top404Uris(@Param("from") LocalDate from,
                                   @Param("to")   LocalDate to,
                                   @Param("limit") int limit);

    /** ⑪ 가장 많이 다운로드된 파일 TOP 20 — log_file_download 기준. value = 다운로드 횟수 */
    List<AccessTopRow> topDownloadFiles(@Param("from") LocalDate from,
                                         @Param("to")   LocalDate to,
                                         @Param("limit") int limit);

    /**
     * ⑫ 가장 많이 방문한 로그인 사용자 TOP 20 — actor_login_id NOT NULL.
     * 헤비 유저 / VIP 식별 + 계정 도용·봇 악용 의심 모니터링.
     * value = 요청 수, valueExtra = distinct IP 수 (한 계정이 여러 IP 에서 들어오면 의심).
     */
    List<AccessTopRow> topLoginUsers(@Param("from") LocalDate from,
                                      @Param("to")   LocalDate to,
                                      @Param("limit") int limit);
}
