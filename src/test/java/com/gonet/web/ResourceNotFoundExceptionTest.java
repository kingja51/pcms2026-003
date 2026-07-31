package com.gonet.web;

import com.gonet.common.web.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * "없는 자원 → 404" 계약 검증.
 *
 * <h2>배경</h2>
 * 사용자 화면의 조회 경로 19개가 {@code try-catch} 없이 서비스를 호출한다.
 * 서비스는 대상이 없을 때 {@code IllegalArgumentException("…을 찾을 수 없습니다")} 를
 * 던졌고, 이를 4xx 로 매핑하는 핸들러가 없어 <b>없는 게시판을 열면 500</b> 이었다.
 *
 * <p>사용자 입력으로 도달 가능한 경로의 5xx 는 오류 로그를 오염시켜
 * 진짜 장애를 묻는다.
 *
 * <h2>이 테스트가 지키는 두 가지</h2>
 * <ol>
 *   <li><b>404 매핑</b> — {@link ResponseStatus} 가 붙어 있어야 한다</li>
 *   <li><b>상속 관계</b> — {@code IllegalArgumentException} 을 상속해야 한다.
 *       기존 {@code catch (IllegalArgumentException)} 가 100곳 넘게 있고
 *       관리자 CUD 규약(try-catch → flash)이 전부 거기 기대고 있다.
 *       상속을 끊으면 <b>관리자 화면이 조용히 500 으로 바뀐다</b></li>
 * </ol>
 *
 * <p>세 번째로, 검증 오류에 이 예외를 잘못 쓰지 않았는지도 본다 —
 * 통째로 404 를 매핑했다면 "비밀번호는 필수입니다" 가 404 가 됐을 것이다.
 */
class ResourceNotFoundExceptionTest {

    private static final Path MAIN = Path.of("src/main/java");

    @Test
    @DisplayName("@ResponseStatus(404) 가 붙어 있다")
    void mappedTo404() {
        ResponseStatus rs = ResourceNotFoundException.class.getAnnotation(ResponseStatus.class);
        assertThat(rs).as("@ResponseStatus 누락 — 없는 자원이 다시 500 이 된다").isNotNull();
        assertThat(rs.value()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("IllegalArgumentException 을 상속한다 — 기존 catch 100여 곳이 계속 잡아야 한다")
    void extendsIllegalArgument() {
        assertThat(IllegalArgumentException.class)
            .as("상속을 끊으면 관리자 CUD 의 catch 가 예외를 놓쳐 500 이 된다")
            .isAssignableFrom(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("검증 오류에는 쓰지 않는다 — 자원 부재만 404 다")
    void notUsedForValidationErrors() {
        // "필수", "이미 사용", "일치하지 않" 류 메시지에 이 예외를 쓰면 오분류다.
        Pattern misuse = Pattern.compile(
            "throw new ResourceNotFoundException\\(\\s*\"[^\"]*(필수|이미 사용|일치하지 않|형식)");

        List<String> violations = new ArrayList<>();
        for (Path f : javaFiles()) {
            Matcher m = misuse.matcher(read(f));
            while (m.find()) violations.add(f.getFileName() + " → " + m.group());
        }
        assertThat(violations)
            .as("검증 오류를 404 로 내보내고 있다 — IllegalArgumentException 이 맞다")
            .isEmpty();
    }

    @Test
    @DisplayName("'찾을 수 없' 메시지가 IllegalArgumentException 으로 남아 있지 않다")
    void notFoundThrowsAreConverted() {
        Pattern leftover = Pattern.compile(
            "throw new IllegalArgumentException\\(\\s*\"[^\"]*찾을 수 없");

        List<String> violations = new ArrayList<>();
        for (Path f : javaFiles()) {
            Matcher m = leftover.matcher(read(f));
            while (m.find()) violations.add(f.getFileName() + " → " + m.group());
        }
        assertThat(violations)
            .as("자원 부재인데 500 이 된다 — ResourceNotFoundException 을 쓸 것")
            .isEmpty();
    }

    // ------------------------------------------------------------------

    private static List<Path> javaFiles() {
        try (Stream<Path> s = Files.walk(MAIN)) {
            List<Path> files = s.filter(Files::isRegularFile)
                                .filter(p -> p.toString().endsWith(".java"))
                                .toList();
            assertThat(files).as("소스를 못 찾았다 — 경로 확인").isNotEmpty();
            return files;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Path p) {
        try {
            return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
