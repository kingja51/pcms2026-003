package com.gonet.common.crypto;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code gopcms.crypto.pii.*} 바인딩.
 *
 * <ul>
 *   <li>{@code master-key} : base64 32바이트. <b>AES-256-GCM 암호화 전용</b>. 운영은 Vault/KMS 경유</li>
 *   <li>{@code hmac-key}   : base64 32바이트. <b>{@code *_hash} 컬럼의 HMAC-SHA256 전용</b></li>
 *   <li>{@code key-id}     : 키 회전 세대 라벨 (예: v1, v2)</li>
 * </ul>
 *
 * <p><b>두 키는 반드시 다른 값을 쓴다</b>(D11, 2026-07-31). 001 은 HMAC 에도 {@code master-key} 를
 * 재사용했으나("별도 키 관리 간소화"), 암호화 키와 MAC 키 공유는 key separation 원칙에 어긋난다.
 *
 * <p>회전 비용이 비대칭이라는 점에 주의한다:
 * <ul>
 *   <li>{@code master-key} — 복호화 → 재암호화로 끝난다(가역)</li>
 *   <li>{@code hmac-key}   — 해시는 단방향이라 {@code master-key} 로 평문을 복원한 뒤 재해시해야 하고,
 *       그동안 완전일치 검색(로그인·중복확인)이 깨진다. 점검 시간에 일괄 수행한다</li>
 * </ul>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "gopcms.crypto.pii")
public class PiiCryptoProperties {

    /** AES-256-GCM 암호화 키. base64 32바이트. */
    private String masterKey;

    /** HMAC-SHA256 키 — {@code *_hash} 컬럼 전용. base64 32바이트. master-key 와 달라야 한다. */
    private String hmacKey;

    /** 키 회전 세대 라벨. */
    private String keyId = "v1";
}
