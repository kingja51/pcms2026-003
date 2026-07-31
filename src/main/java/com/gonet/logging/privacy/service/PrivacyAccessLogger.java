package com.gonet.logging.privacy.service;

import com.gonet.logging.privacy.dto.PrivacyAccessEvent;

/**
 * 개인정보 접근 이력 적재 단일 진입점.
 *
 * <p>구현체는 비동기(@Async) + REQUIRES_NEW 트랜잭션으로 INSERT — 호출자(primary TX)
 * 가 롤백되어도 이력은 보존된다. (PIPA 위반 사항 방지)
 *
 * <p>호출 패턴:
 * <pre>
 * privacyAccessLogger.write(
 *   PrivacyAccessEvent.of(PrivacyAccessLog.ACTION_EXPORT, "tb_member")
 *     .withCount(rows.size())
 *     .withFields("name,email,phone")
 *     .withReason(excelReq.getDownloadReason())
 * );
 * </pre>
 */
public interface PrivacyAccessLogger {

    void write(PrivacyAccessEvent event);
}
