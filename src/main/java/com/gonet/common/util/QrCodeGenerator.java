package com.gonet.common.util;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * QR 코드 PNG 생성 — ZXing 기반.
 *
 * <p>현재 사용처: 관리자 2FA 셀프 등록 화면(otpauth URL → 스캔 가능한 QR 이미지).
 * inline data URL 형식으로 반환하여 CSP 추가 변경 없이 {@code <img src>} 직접 바인딩.
 */
public final class QrCodeGenerator {

    private QrCodeGenerator() {}

    /**
     * 텍스트(예: {@code otpauth://...} URL) → base64 인코딩된 data URL.
     *
     * @param text 인코딩할 원본 문자열
     * @param size 출력 정사각형 한 변 픽셀 (권장 200~320)
     * @return {@code data:image/png;base64,...} 형식의 inline 이미지 URL
     */
    public static String toPngDataUrl(String text, int size) {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            Map<EncodeHintType, Object> hints = Map.of(
                EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name(),
                EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN, 1
            );
            BitMatrix matrix = writer.encode(text, BarcodeFormat.QR_CODE, size, size, hints);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", baos);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (WriterException | IOException e) {
            throw new IllegalStateException("QR 코드 생성 실패", e);
        }
    }
}
