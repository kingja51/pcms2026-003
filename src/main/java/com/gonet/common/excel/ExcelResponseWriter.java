package com.gonet.common.excel;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * 엑셀(.xlsx) 응답 공통 작성기.
 *
 * <p>중복 제거 대상이었던 헬퍼(파일명 타임스탬프/UTF-8 Content-Disposition/SXSSF 설정·flush·dispose/
 * 헤더 스타일) 를 한 곳으로 모은다. {@code MngController} 의 excel 엔드포인트는
 * {@link #write(HttpServletResponse, String, String, String[], List, BiConsumer)}
 * 한 번 호출로 끝난다.
 *
 * <pre>
 * String excelFilename = excelWriter.write(res, "사이트", "gopcms_sites",
 *     new String[]{"코드","이름","도메인", ...},
 *     rows,
 *     (row, s) -> {
 *         row.createCell(0).setCellValue(nvl(s.getSiteCode()));
 *         row.createCell(1).setCellValue(nvl(s.getSiteName()));
 *         ...
 *     });
 * </pre>
 *
 * <p>SXSSF window size 100 (메모리 상주 100행) — 대량 다운로드에서 OOM 방어.
 * <p>파일명: {@code {prefix}_{yyyyMMdd_HHmmss}.xlsx}, RFC 5987 UTF-8 인코딩.
 */
@Component
public class ExcelResponseWriter {

    private static final DateTimeFormatter TS =
        DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final int DEFAULT_COLUMN_WIDTH = 5_000;
    /**
     * SXSSF window — 메모리 상주 행 수. 작을수록 메모리↓·디스크 IO↑.
     * 100 행이면 통상 수 KB 메모리. 대용량 엑셀(수만 행)에서도 OOM 회피.
     */
    private static final int SXSSF_WINDOW         = 100;

    public <T> String write(HttpServletResponse res,
                           String sheetName,
                           String fileNamePrefix,
                           String[] headers,
                           List<T> rows,
                           BiConsumer<Row, T> rowMapper) throws IOException {
        // writeHeaders가 생성한 파일명을 받습니다.
        String generatedFileName = writeHeaders(res, fileNamePrefix);

        try (SXSSFWorkbook wb = new SXSSFWorkbook(SXSSF_WINDOW)) {
            // M-4 — 임시 파일도 압축하여 대용량 다운로드 시 디스크 사용량 절감.
            wb.setCompressTempFiles(true);
            Sheet sheet = wb.createSheet(sheetName);
            writeHeaderRow(wb, sheet, headers);

            int r = 1;
            for (T item : rows) {
                Row row = sheet.createRow(r++);
                rowMapper.accept(row, item);
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.setColumnWidth(i, DEFAULT_COLUMN_WIDTH);
            }

            wb.write(res.getOutputStream());
            wb.dispose(); // SXSSF 임시파일 정리
        }
        // 컨트롤러로 파일명 반환
        return generatedFileName;
    }

    /**
     * 다중 시트 엑셀 — 한 화면에 여러 표가 있을 때 표 1개당 시트 1개로 모두 내보낸다.
     *
     * <p>각 {@link SheetSpec} 이 시트명·헤더·데이터·행 매퍼를 담는다.
     * 단일 표 화면은 {@link #write} 를, 다중 표 화면(예: 회계별 예산규모 4표)은 본 메서드를 쓴다.
     */
    public String writeSheets(HttpServletResponse res,
                              String fileNamePrefix,
                              List<SheetSpec<?>> sheets) throws IOException {
        String generatedFileName = writeHeaders(res, fileNamePrefix);

        try (SXSSFWorkbook wb = new SXSSFWorkbook(SXSSF_WINDOW)) {
            wb.setCompressTempFiles(true);
            CellStyle headerStyle = headerCellStyle(wb);
            for (SheetSpec<?> spec : sheets) {
                renderSheet(wb, headerStyle, spec);
            }
            wb.write(res.getOutputStream());
            wb.dispose();
        }
        return generatedFileName;
    }

    private <T> void renderSheet(SXSSFWorkbook wb, CellStyle headerStyle, SheetSpec<T> spec) {
        Sheet sheet = wb.createSheet(spec.sheetName());
        String[] headers = spec.headers();

        Row h = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell c = h.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(headerStyle);
        }
        int r = 1;
        for (T item : spec.rows()) {
            Row row = sheet.createRow(r++);
            spec.rowMapper().accept(row, item);
        }
        for (int i = 0; i < headers.length; i++) {
            sheet.setColumnWidth(i, DEFAULT_COLUMN_WIDTH);
        }
    }

    private CellStyle headerCellStyle(SXSSFWorkbook wb) {
        Font bold = wb.createFont();
        bold.setBold(true);
        CellStyle style = wb.createCellStyle();
        style.setFont(bold);
        return style;
    }

    /**
     * 다중 시트 엑셀의 시트 1개 분량 — 시트명 + 헤더 + 데이터 + 행 매퍼.
     *
     * @param <T> 해당 시트 데이터 행 타입
     */
    public record SheetSpec<T>(String sheetName,
                               String[] headers,
                               List<T> rows,
                               BiConsumer<Row, T> rowMapper) {}

    private String writeHeaders(HttpServletResponse res, String fileNamePrefix) {
        String fileName = fileNamePrefix + "_" + LocalDateTime.now().format(TS) + ".xlsx";
        String encoded  = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");

        res.setContentType(MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet").toString());
        res.setHeader(HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"" + fileName + "\"; filename*=UTF-8''" + encoded);
        return fileName; // 파일명 반환 추가
    }

    private void writeHeaderRow(SXSSFWorkbook wb, Sheet sheet, String[] headers) {
        Font bold = wb.createFont();
        bold.setBold(true);
        CellStyle style = wb.createCellStyle();
        style.setFont(bold);

        Row h = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell c = h.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(style);
        }
    }

    /** 공통 nvl — 엑셀 셀에 null 대신 빈 문자열 세팅. */
    public static String nvl(String s) { return s == null ? "" : s; }

    /** LocalDateTime → yyyy-MM-dd HH:mm:ss (null 안전). 사람 친화 포맷 (엑셀 셀 가독성). */
    private static final DateTimeFormatter DT_HUMAN =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static String str(LocalDateTime t) {
        return t == null ? "" : t.format(DT_HUMAN);
    }
}
