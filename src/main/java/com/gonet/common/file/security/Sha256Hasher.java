package com.gonet.common.file.security;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 방어 #4 — SHA-256 File Integrity Monitoring (FIM).
 *
 * <p>업로드 직후 파일 바이트의 SHA-256 을 계산해 {@code tb_file.file_hash} 에 저장.
 * 스토리지 tampering 감지 + 동일 그룹 내 중복 업로드 억제 + (옵션) 전역 hash denylist
 * 와의 대조에 사용.
 */
@Component
public class Sha256Hasher {

    private static final int BUFFER = 8192;

    /** 이미 저장된 파일의 해시. */
    public String hash(Path path) {
        try (InputStream in = Files.newInputStream(path)) {
            return hash(in);
        } catch (IOException ex) {
            throw new IllegalStateException("SHA-256 계산 실패: " + path, ex);
        }
    }

    /**
     * 스트림의 SHA-256 을 16진 소문자 64자 리턴. 스트림은 끝까지 소비됨.
     */
    public String hash(InputStream in) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[BUFFER];
            int r;
            while ((r = in.read(buf)) > 0) {
                md.update(buf, 0, r);
            }
            return toHex(md.digest());
        } catch (NoSuchAlgorithmException | IOException ex) {
            throw new IllegalStateException("SHA-256 계산 실패", ex);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
