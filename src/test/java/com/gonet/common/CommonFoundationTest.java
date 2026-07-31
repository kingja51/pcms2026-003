package com.gonet.common;

import com.gonet.common.crypto.AesGcmCipher;
import com.gonet.common.crypto.EmailHasher;
import com.gonet.common.crypto.PiiCryptoProperties;
import com.gonet.common.file.config.FileUploadProperties;
import com.gonet.common.file.security.FileStorage;
import com.gonet.common.util.MaskUtils;
import com.gonet.common.util.UuidV7Generator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P1 공통 기반 계층 DoD 검증.
 *
 * <p>Spring 컨텍스트를 띄우지 않는 순수 단위 테스트다 — DB·환경변수에 의존하지 않는다.
 */
class CommonFoundationTest {

    /** 테스트 전용 키 — base64 디코드 시 정확히 32바이트(AES-256)여야 한다. 운영 키와 무관. */
    private static final String KEY_A = Base64.getEncoder().encodeToString("aes-key-0123456789abcdef01234567".getBytes());
    private static final String KEY_B = Base64.getEncoder().encodeToString("hmac-key-0123456789abcdef0123456".getBytes());

    private static PiiCryptoProperties props() {
        PiiCryptoProperties p = new PiiCryptoProperties();
        p.setMasterKey(KEY_A);
        p.setHmacKey(KEY_B);
        return p;
    }

    @Test
    @DisplayName("암호화 왕복 — 평문 → {AG} 암호문 → 복호화")
    void aesGcmRoundTrip() {
        AesGcmCipher cipher = new AesGcmCipher(props());
        String plain = "홍길동 010-1234-5678";

        String enc = cipher.encrypt(plain);

        assertThat(enc).startsWith(AesGcmCipher.PREFIX);
        assertThat(enc).doesNotContain(plain);
        assertThat(cipher.decryptIfEncrypted(enc)).isEqualTo(plain);
    }

    @Test
    @DisplayName("AES-GCM 은 매 암호화마다 IV 가 달라 같은 평문도 다른 암호문이 된다")
    void aesGcmUsesRandomIv() {
        AesGcmCipher cipher = new AesGcmCipher(props());

        String a = cipher.encrypt("same-plaintext");
        String b = cipher.encrypt("same-plaintext");

        assertThat(a).isNotEqualTo(b);
        assertThat(cipher.decryptIfEncrypted(a)).isEqualTo(cipher.decryptIfEncrypted(b));
    }

    @Test
    @DisplayName("{AG} 프리픽스가 없으면 legacy 평문으로 그대로 반환한다")
    void decryptPassesThroughLegacyPlaintext() {
        assertThat(new AesGcmCipher(props()).decryptIfEncrypted("legacy-plain")).isEqualTo("legacy-plain");
    }

    @Test
    @DisplayName("HMAC 해시 — 결정적이고, 대소문자·공백을 정규화한다")
    void emailHashIsDeterministicAndNormalized() {
        EmailHasher hasher = new EmailHasher(props());

        String h = hasher.hash("User@Example.COM");

        assertThat(h).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(hasher.hash("  user@example.com  ")).isEqualTo(h);
    }

    @Test
    @DisplayName("D11 — HMAC 은 hmac-key 를 쓴다. master-key 만으로는 생성되지 않는다")
    void emailHasherRequiresHmacKey() {
        PiiCryptoProperties onlyMaster = new PiiCryptoProperties();
        onlyMaster.setMasterKey(KEY_A);

        assertThatThrownBy(() -> new EmailHasher(onlyMaster))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("hmac-key");
    }

    @Test
    @DisplayName("D11 — 같은 입력이라도 master-key 와 hmac-key 가 다르면 해시가 다르다(키 분리 확인)")
    void hmacKeyIsActuallySeparate() {
        PiiCryptoProperties reused = new PiiCryptoProperties();
        reused.setMasterKey(KEY_A);
        reused.setHmacKey(KEY_A);   // 001 방식 — 재사용

        assertThat(new EmailHasher(props()).hash("a@b.com"))
            .isNotEqualTo(new EmailHasher(reused).hash("a@b.com"));
    }

    @Test
    @DisplayName("경로 조작 차단 — ../ 로 루트를 벗어나면 거부한다")
    void pathTraversalIsBlocked(@TempDir Path tmp) {
        FileUploadProperties p = new FileUploadProperties();
        p.setRoot(tmp.resolve("uploads").toString());
        p.setQuarantine(tmp.resolve("quarantine").toString());
        p.setThumbnail(tmp.resolve("thumb").toString());
        FileStorage storage = new FileStorage(p);
        storage.init();   // @PostConstruct — 루트 확정 + 디렉터리 생성

        assertThatThrownBy(() -> storage.resolveStoredPath("../../etc/passwd"))
            .isInstanceOf(Exception.class);
        assertThatThrownBy(() -> storage.resolveStoredPath("a/../../../outside.txt"))
            .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("정상 상대경로는 업로드 루트 안으로 해석된다")
    void normalRelativePathResolvesInsideRoot(@TempDir Path tmp) {
        FileUploadProperties p = new FileUploadProperties();
        p.setRoot(tmp.resolve("uploads").toString());
        p.setQuarantine(tmp.resolve("quarantine").toString());
        p.setThumbnail(tmp.resolve("thumb").toString());
        FileStorage storage = new FileStorage(p);
        storage.init();   // @PostConstruct — 루트 확정 + 디렉터리 생성

        Path resolved = storage.resolveStoredPath("BBS/2026/07/abc.png");

        assertThat(resolved.normalize())
            .startsWithRaw(tmp.resolve("uploads").toAbsolutePath().normalize());
    }

    @Test
    @DisplayName("마스킹 — surrogate 문자(이모지)가 섞여도 깨지지 않는다")
    void maskingHandlesSurrogatePairs() {
        String withEmoji = "홍길동👨‍👩‍👧";

        String masked = MaskUtils.name(withEmoji);

        assertThat(masked).isNotNull();
        // 서로게이트 페어를 쪼개면 U+FFFD(replacement char) 가 생긴다.
        assertThat(masked).doesNotContain("�");
    }

    @Test
    @DisplayName("UUID v7 — 36자 형식이고 버전 니블이 7, 타임스탬프 구간이 시간 순이다")
    void uuidV7IsTimeOrdered() throws InterruptedException {
        String a = UuidV7Generator.generate();
        Thread.sleep(2);
        String b = UuidV7Generator.generate();

        assertThat(a).hasSize(36);
        assertThat(a.charAt(14)).isEqualTo('7');
        // 앞 13자(48비트 밀리초 타임스탬프)만 비교한다.
        // 같은 밀리초 안에서는 뒤쪽 랜덤 비트 때문에 전체 문자열 순서가 보장되지 않는다.
        assertThat(a.substring(0, 13)).isLessThanOrEqualTo(b.substring(0, 13));
    }
}
