package com.gonet.common.util;

import com.fasterxml.jackson.core.SerializableString;
import com.fasterxml.jackson.core.io.CharacterEscapes;
import com.fasterxml.jackson.core.io.SerializedString;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * HTML 문서 내부(특히 {@code <script type="application/json">…</script>})에 삽입해도
 * 안전한 JSON 을 만드는 {@link ObjectMapper} 팩토리.
 *
 * <p>기본 {@code new ObjectMapper()} 는 {@code <}, {@code >}, {@code &}, {@code /} 를
 * 이스케이프하지 않는다. 사용자 입력이 페이로드 문자열에 섞여 {@code th:utext} 로
 * script 블록에 렌더되면 {@code </script><script>…</script>} 브레이크아웃으로
 * 저장형 XSS 가 성립한다.
 *
 * <p>001 에서 실제로 발생했다 — 익명 사용자가 검색어에 스크립트를 넣어 저장하고,
 * 관리자 통계 화면에서 실행됐다. 003 은 검색을 외부 엔진으로 분리했지만(D10)
 * 사용자 입력을 script 컨텍스트에 넣는 경로 전반에 같은 위험이 있다.
 *
 * <p>본 팩토리는 U+2028/U+2029(줄구분자)까지 포함해 이 문자들을 {@code \\uXXXX} 로
 * 이스케이프하므로 script 컨텍스트 삽입이 안전하다. 값 자체는 JSON 문자열로 보존된다.
 */
public final class HtmlSafeJson {

    private HtmlSafeJson() {}

    /** script/HTML 컨텍스트에 안전한 새 ObjectMapper. 인스턴스는 스레드-세이프하므로 재사용 가능. */
    public static ObjectMapper mapper() {
        ObjectMapper om = new ObjectMapper();
        om.getFactory().setCharacterEscapes(new HtmlCharacterEscapes());
        return om;
    }

    /** {@code <>&/} + 줄구분자(U+2028/2029) 를 \\uXXXX 로 강제 이스케이프. */
    private static final class HtmlCharacterEscapes extends CharacterEscapes {

        private final int[] asciiEscapes;

        HtmlCharacterEscapes() {
            int[] esc = CharacterEscapes.standardAsciiEscapesForJSON();
            esc['<'] = CharacterEscapes.ESCAPE_STANDARD;
            esc['>'] = CharacterEscapes.ESCAPE_STANDARD;
            esc['&'] = CharacterEscapes.ESCAPE_STANDARD;
            esc['/'] = CharacterEscapes.ESCAPE_STANDARD; // </script> 의 '/' 차단
            this.asciiEscapes = esc;
        }

        @Override
        public int[] getEscapeCodesForAscii() {
            return asciiEscapes;
        }

        @Override
        public SerializableString getEscapeSequence(int ch) {
            // 줄구분자는 JS 문자열 리터럴을 깨므로 함께 이스케이프.
            if (ch == 0x2028) return new SerializedString("\\u2028");
            if (ch == 0x2029) return new SerializedString("\\u2029");
            return null;
        }
    }
}
