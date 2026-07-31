package com.gonet.scheduler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 스케줄러 규약 게이트 — CLAUDE.md "cron 은 {@code ${…:기본값}} 으로 외부화하고
 * dry-run 플래그를 둔다".
 *
 * <p>이 검사가 필요한 이유: 001 의 {@code DormantScheduler} 는 cron 이
 * {@code "0 0 1 * * *"} 로 <b>하드코딩</b>돼 있었고 아무도 눈치채지 못했다.
 * 스케줄러는 평소에 조용해서, 규약을 어겨도 화면처럼 티가 나지 않는다.
 * 배포 없이 시각을 못 바꾼다는 사실은 정작 <b>급히 꺼야 할 때</b> 드러난다.
 *
 * <p>ArchUnit 이 아니라 소스 텍스트를 보는 이유: {@code @Scheduled} 의 cron 은
 * 컴파일 상수라 바이트코드만 봐서는 {@code "${...}"} 자리표시자인지 리터럴인지
 * 구분되지만, <b>기본값 문법({@code :기본값})까지</b> 확인하려면 문자열을 직접 보는
 * 편이 정확하다.
 */
class SchedulerContractTest {

    private static final Path SCHEDULER_DIR = Path.of("src/main/java/com/gonet/scheduler");

    /** 파괴적 배치 — 되돌릴 수 없으므로 dry-run 이 있어야 한다. */
    private static final List<String> DESTRUCTIVE = List.of(
        "DormantScheduler",             // 회원을 tb_member 밖으로 옮기고 만료분 파기
        "SoftDeleteRetentionScheduler", // soft-delete 행 hard delete
        "WithdrawPurgeScheduler",       // 탈퇴 회원 개인정보 파기
        "LogRetentionScheduler"         // 감사·접속·로그인 로그 삭제
    );

    /**
     * 되살아나면 안 되는 스케줄러 — 지운 데는 이유가 있다.
     *
     * <p>{@code FilePurgeScheduler} 는 {@code FileRetentionTarget} 과 <b>같은 정리를
     * 서로 다른 cutoff·중단 스위치로</b> 돌렸다. {@code retention.dry-run=true} 로 꺼도
     * 04:00 실행은 막히지 않아 "껐는데 파일이 지워졌다" 가 가능했다.
     * 2026-08-01 에 트리거를 {@code FileRetentionTarget} 하나로 통일했다.
     *
     * <p>나머지 둘은 도메인 자체가 없다 — GenAI SDK 미도입, 날씨 미이식.
     */
    private static final List<String> MUST_NOT_RETURN = List.of(
        "FilePurgeScheduler",
        "GeminiFileRenewScheduler",
        "WeatherCollectScheduler"
    );

    @Test
    @DisplayName("모든 @Scheduled cron 은 ${...:기본값} 으로 외부화돼 있다")
    void everyCronIsExternalized() {
        List<String> violations = new ArrayList<>();

        for (Path f : schedulerFiles()) {
            String src = read(f);
            for (String cron : cronExpressions(src)) {
                if (!cron.startsWith("${")) {
                    violations.add(f.getFileName() + " → 하드코딩 cron: " + cron);
                } else if (!cron.contains(":")) {
                    // 기본값이 없으면 프로퍼티 미설정 시 기동이 깨진다
                    violations.add(f.getFileName() + " → 기본값 없는 자리표시자: " + cron);
                }
            }
        }
        assertThat(violations).as("cron 외부화 위반").isEmpty();
    }

    @Test
    @DisplayName("파괴적 배치에는 dry-run 플래그가 있다 — 켜기 전에 대상을 볼 수 있어야 한다")
    void destructiveSchedulersHaveDryRun() {
        List<String> missing = new ArrayList<>();

        for (String name : DESTRUCTIVE) {
            Path f = SCHEDULER_DIR.resolve(name + ".java");
            assertThat(f).as(name + " 존재").exists();
            String src = read(f);
            boolean hasDryRun = src.contains("dryRun") || src.contains("dry-run");
            if (!hasDryRun) missing.add(name);
        }
        assertThat(missing).as("dry-run 이 없는 파괴적 배치").isEmpty();
    }

    @Test
    @DisplayName("모든 스케줄 메서드에 @SchedulerLock 이 붙어 있다 — 다중 인스턴스 중복 실행 방지")
    void everyScheduledMethodIsLocked() {
        List<String> violations = new ArrayList<>();

        for (Path f : schedulerFiles()) {
            String src = read(f);
            int scheduled = countOccurrences(src, "@Scheduled");
            int locked    = countOccurrences(src, "@SchedulerLock");
            if (locked < scheduled) {
                violations.add("%s → @Scheduled %d개 중 @SchedulerLock %d개"
                    .formatted(f.getFileName(), scheduled, locked));
            }
        }
        assertThat(violations).as("ShedLock 누락").isEmpty();
    }

    @Test
    @DisplayName("제외·삭제한 스케줄러가 되살아나지 않았다")
    void removedSchedulersStayRemoved() {
        for (String name : MUST_NOT_RETURN) {
            assertThat(SCHEDULER_DIR.resolve(name + ".java"))
                .as(name + " 는 되살아나면 안 된다 — javadoc 참조").doesNotExist();
        }
    }

    @Test
    @DisplayName("파일 정리 트리거는 하나뿐이다 — FileRetentionTarget 경유")
    void filePurgeHasSingleTrigger() {
        // 스케줄러가 FilePurgeService 를 직접 부르면 트리거가 다시 둘이 된다.
        for (Path f : schedulerFiles()) {
            assertThat(read(f))
                .as(f.getFileName() + " 가 FilePurgeService 를 직접 호출한다")
                .doesNotContain("FilePurgeService");
        }
    }

    // ------------------------------------------------------------------

    private static List<Path> schedulerFiles() {
        try (Stream<Path> s = Files.list(SCHEDULER_DIR)) {
            List<Path> files = s.filter(p -> p.toString().endsWith(".java")).toList();
            assertThat(files).as("스케줄러 파일이 하나도 없다 — 경로 확인").isNotEmpty();
            return files;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** {@code @Scheduled(cron = "...")} 의 cron 문자열만 뽑는다. */
    private static List<String> cronExpressions(String src) {
        List<String> out = new ArrayList<>();
        java.util.regex.Matcher m =
            java.util.regex.Pattern.compile("@Scheduled\\s*\\(\\s*cron\\s*=\\s*\"([^\"]*)\"").matcher(src);
        while (m.find()) out.add(m.group(1));
        return out;
    }

    private static int countOccurrences(String src, String token) {
        return src.split(java.util.regex.Pattern.quote(token), -1).length - 1;
    }

    private static String read(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** {@link Scheduled}·{@link Method} 는 의도 문서화를 위한 참조다(런타임 미사용). */
    @SuppressWarnings("unused")
    private static final Class<?>[] DOC_REFS = { Scheduled.class, Method.class };
}
