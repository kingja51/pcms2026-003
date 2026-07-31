package com.gonet.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 매퍼 XML 이 참조하는 컬럼이 실제 스키마에 있는지 검사한다.
 *
 * <h2>왜 필요한가</h2>
 * 003 은 베이스라인 DDL 을 001 에서 재설계했는데 <b>코드는 001 형태 그대로</b>
 * 이식했다. 그 결과 존재하지 않는 컬럼을 SELECT 하는 질의가 남았다:
 * <ul>
 *   <li>{@code tb_template.layout_path} — 17곳 (V2026080104 로 복원)</li>
 *   <li>{@code tb_site.default_template_id} — 14곳 (V2026080105 로 리네임)</li>
 *   <li>{@code tb_site.theme} — 2곳 (V2026080105 로 신설)</li>
 * </ul>
 *
 * <p><b>컴파일도 ArchUnit 도 이걸 못 잡는다.</b> SQL 은 문자열이라 실행해야
 * 드러나고, 증상은 화면 500 이다. 실제로 P8 에서 앱을 띄워 {@code /bbs/...} 를
 * 찔러보고서야 알았다 — 그전까지 테스트 53건이 전부 통과하고 있었다.
 *
 * <h2>검사 방법</h2>
 * DDL 덤프({@code sql/*.sql})와 Flyway 마이그레이션에서 테이블별 컬럼 집합을 만들고,
 * 매퍼의 {@code FROM/JOIN <table> <alias>} 로 alias→table 을 해석해
 * {@code alias.column} 참조를 대조한다.
 *
 * <p>alias 를 해석할 수 없는 참조는 <b>건너뛴다</b> — 파생 테이블·CTE 별칭까지
 * 정확히 추적하려면 SQL 파서가 필요하고, 오탐이 늘면 이 검사를 끄게 된다.
 * 놓치는 것이 있어도 "있는 것만 확실히 잡는" 쪽을 택했다.
 */
class MapperSchemaConsistencyTest {

    private static final Path SQL_DIR       = Path.of("sql");
    private static final Path MIGRATION_DIR = Path.of("src/main/resources/db/migration");
    private static final Path MAPPER_DIR    = Path.of("src/main/resources/mapper");

    private static final Pattern CREATE_TABLE =
        Pattern.compile("CREATE TABLE `(\\w+)` \\((.*?)\\n\\) ENGINE", Pattern.DOTALL);
    private static final Pattern COLUMN_DEF   = Pattern.compile("^\\s+`(\\w+)`\\s+\\w", Pattern.MULTILINE);
    private static final Pattern VIEW_ALIAS   = Pattern.compile("AS\\s+`(\\w+)`");
    private static final Pattern ADD_COLUMN   = Pattern.compile("ALTER TABLE `(\\w+)`\\s+ADD COLUMN `(\\w+)`");
    private static final Pattern DROP_COLUMN  = Pattern.compile("ALTER TABLE `(\\w+)`\\s+DROP COLUMN `(\\w+)`");
    private static final Pattern CHANGE_COLUMN =
        Pattern.compile("ALTER TABLE `(\\w+)`\\s+CHANGE COLUMN `(\\w+)`\\s+`(\\w+)`");
    private static final Pattern FROM_JOIN =
        Pattern.compile("\\b(?:FROM|JOIN)\\s+(\\w+)\\s+(?:AS\\s+)?(\\w+)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern QUALIFIED_COL = Pattern.compile("\\b(\\w+)\\.(\\w+)\\b");
    private static final Pattern XML_COMMENT   = Pattern.compile("<!--.*?-->", Pattern.DOTALL);
    private static final Pattern SQL_FRAGMENT  = Pattern.compile("<sql id=\"(\\w+)\">(.*?)</sql>", Pattern.DOTALL);
    private static final Pattern SELECT_BLOCK  = Pattern.compile("<select id=\"(\\w+)\".*?</select>", Pattern.DOTALL);
    private static final Pattern INCLUDE_REF   = Pattern.compile("<include\\s+refid=\"(\\w+)\"\\s*/>");

    private static final Set<String> SQL_KEYWORDS =
        Set.of("on", "and", "or", "as", "is", "not", "null", "from", "join", "where", "select");

    /**
     * 알려진 오탐 — 재귀 CTE 가 만드는 파생 컬럼. 실제 테이블 컬럼이 아니다.
     * {@code DepartmentMapper} 의 트리 DFS 정렬용 {@code tree_path} 계열.
     */
    private static final Set<String> KNOWN_DERIVED = Set.of("tree_path", "chain_depth");

    /**
     * {@code <sql>} 조각이 쓰는 별칭 — 조각을 include 하는 select 는 그 별칭을
     * 반드시 조인해야 한다.
     *
     * <p>이 검사를 따로 두는 이유: 컬럼 존재 검사만으로는 못 잡는다.
     * 조각에 {@code th.theme_code} 를 넣고 어떤 select 가 {@code tb_theme} 를
     * 조인하지 않으면, {@code th} 는 <b>미해석 별칭이라 그냥 건너뛰어진다</b>.
     * 실제로 {@code findSiteIdByMenuId} 가 이 상태였다 —
     * 조각에만 별칭을 추가하고 tb_menu 에서 출발하는 그 질의를 빠뜨렸다.
     * 실행하면 "Unknown column 'th.theme_code'" 로 죽는다.
     */
    private static final Map<String, String> FRAGMENT_ALIAS = Map.of(
        "th", "tb_theme",
        "t",  "tb_template"
    );

    @Test
    @DisplayName("sql 조각이 쓰는 별칭을 include 하는 select 가 전부 조인한다")
    void everyFragmentAliasIsJoined() {
        List<String> violations = new ArrayList<>();

        for (Path mapper : mapperFiles()) {
            String src = XML_COMMENT.matcher(read(mapper)).replaceAll("");

            // 조각별로 어떤 별칭을 쓰는지
            Map<String, Set<String>> fragmentAliases = new HashMap<>();
            Matcher frag = SQL_FRAGMENT.matcher(src);
            while (frag.find()) {
                Set<String> used = new HashSet<>();
                Matcher c = QUALIFIED_COL.matcher(frag.group(2));
                while (c.find()) {
                    if (FRAGMENT_ALIAS.containsKey(c.group(1))) used.add(c.group(1));
                }
                if (!used.isEmpty()) fragmentAliases.put(frag.group(1), used);
            }
            if (fragmentAliases.isEmpty()) continue;

            // 모든 <sql> 조각 본문 — include 를 펼치는 데 쓴다
            Map<String, String> fragmentBody = new HashMap<>();
            Matcher all = SQL_FRAGMENT.matcher(src);
            while (all.find()) fragmentBody.put(all.group(1), all.group(2));

            Matcher sel = SELECT_BLOCK.matcher(src);
            while (sel.find()) {
                // include 를 **실제로 펼친 뒤** 판정한다.
                // 예전에는 "FROM 조각을 include 하면 통과" 로 봐줬는데, 그 조각이
                // 정말 조인하는지 확인하지 않아 검사가 무력했다 —
                // siteFrom 의 조인을 지워도 통과했다.
                String expanded = expandIncludes(sel.group(0), fragmentBody);

                for (var e : fragmentAliases.entrySet()) {
                    if (!sel.group(0).contains("refid=\"" + e.getKey() + "\"")) continue;
                    for (String alias : e.getValue()) {
                        String table = FRAGMENT_ALIAS.get(alias);
                        if (!expanded.contains(table)) {
                            violations.add("%s → %s 가 조각 %s 의 별칭 %s(%s)를 조인하지 않는다"
                                .formatted(mapper.getFileName(), sel.group(1), e.getKey(), alias, table));
                        }
                    }
                }
            }
        }

        assertThat(violations)
            .as("조각이 쓰는 별칭을 조인하지 않는다 — 실행 시 Unknown column 으로 죽는다")
            .isEmpty();
    }

    @Test
    @DisplayName("매퍼가 참조하는 컬럼이 전부 스키마에 있다")
    void everyReferencedColumnExists() {
        Map<String, Set<String>> tables = loadSchema();
        assertThat(tables).as("DDL 을 하나도 못 읽었다 — 경로 확인").isNotEmpty();

        List<String> violations = new ArrayList<>();

        for (Path mapper : mapperFiles()) {
            String sql = XML_COMMENT.matcher(read(mapper)).replaceAll("");

            Map<String, String> aliasToTable = new HashMap<>();
            Matcher fj = FROM_JOIN.matcher(sql);
            while (fj.find()) {
                String table = fj.group(1);
                String alias = fj.group(2);
                if (SQL_KEYWORDS.contains(alias.toLowerCase(Locale.ROOT))) continue;
                if (tables.containsKey(table)) aliasToTable.put(alias, table);
            }

            Matcher qc = QUALIFIED_COL.matcher(sql);
            while (qc.find()) {
                String table = aliasToTable.get(qc.group(1));
                if (table == null) continue;                       // alias 미해석 — 건너뛴다
                String col = qc.group(2).toLowerCase(Locale.ROOT);
                if (KNOWN_DERIVED.contains(col)) continue;
                if (tables.get(table).contains(col)) continue;
                violations.add("%s → %s.%s".formatted(mapper.getFileName(), table, col));
            }
        }

        assertThat(new HashSet<>(violations))
            .as("스키마에 없는 컬럼을 SELECT 한다 — 실행 시 500 이 된다")
            .isEmpty();
    }

    /** {@code <include refid="x"/>} 를 조각 본문으로 치환한다(1단계면 충분하다). */
    private static String expandIncludes(String body, Map<String, String> fragments) {
        Matcher inc = INCLUDE_REF.matcher(body);
        StringBuilder sb = new StringBuilder();
        while (inc.find()) {
            inc.appendReplacement(sb, Matcher.quoteReplacement(
                fragments.getOrDefault(inc.group(1), "")));
        }
        inc.appendTail(sb);
        return sb.toString();
    }

    // ------------------------------------------------------------------

    /** DDL 덤프 + 마이그레이션을 합쳐 테이블별 컬럼 집합(소문자)을 만든다. */
    private static Map<String, Set<String>> loadSchema() {
        Map<String, Set<String>> tables = new HashMap<>();

        for (Path f : listFiles(SQL_DIR, ".sql")) {
            String s = read(f);
            Matcher m = CREATE_TABLE.matcher(s);
            while (m.find()) {
                Set<String> cols = new HashSet<>();
                Matcher c = COLUMN_DEF.matcher(m.group(2));
                while (c.find()) cols.add(c.group(1).toLowerCase(Locale.ROOT));
                tables.put(m.group(1), cols);
            }
        }

        // 뷰 — SELECT 별칭이 곧 컬럼이다
        for (Path f : listFiles(SQL_DIR, ".sql")) {
            collectViews(read(f), tables);
        }
        for (Path f : listFiles(MIGRATION_DIR, ".sql")) {
            collectViews(read(f), tables);
        }

        // 마이그레이션의 컬럼 추가·삭제·리네임 반영 (버전 순서대로)
        for (Path f : listFiles(MIGRATION_DIR, ".sql")) {
            String s = read(f);
            applyAll(ADD_COLUMN, s, (t, c) -> tables.computeIfAbsent(t, k -> new HashSet<>()).add(c));
            applyAll(DROP_COLUMN, s, (t, c) -> tables.getOrDefault(t, new HashSet<>()).remove(c));
            Matcher ch = CHANGE_COLUMN.matcher(s);
            while (ch.find()) {
                Set<String> cols = tables.computeIfAbsent(ch.group(1), k -> new HashSet<>());
                cols.remove(ch.group(2).toLowerCase(Locale.ROOT));
                cols.add(ch.group(3).toLowerCase(Locale.ROOT));
            }
        }
        return tables;
    }

    private static void collectViews(String s, Map<String, Set<String>> tables) {
        Matcher v = Pattern.compile("CREATE OR REPLACE VIEW `(\\w+)`(.*?)(?:;|\\z)", Pattern.DOTALL).matcher(s);
        while (v.find()) {
            Set<String> cols = new HashSet<>();
            Matcher a = VIEW_ALIAS.matcher(v.group(2));
            while (a.find()) cols.add(a.group(1).toLowerCase(Locale.ROOT));
            if (!cols.isEmpty()) tables.put(v.group(1), cols);
        }
    }

    private interface ColumnOp { void apply(String table, String column); }

    private static void applyAll(Pattern p, String s, ColumnOp op) {
        Matcher m = p.matcher(s);
        while (m.find()) op.apply(m.group(1), m.group(2).toLowerCase(Locale.ROOT));
    }

    private static List<Path> mapperFiles() {
        List<Path> files = listFiles(MAPPER_DIR, "_maria.xml");
        assertThat(files).as("매퍼 XML 을 하나도 못 찾았다 — 경로 확인").isNotEmpty();
        return files;
    }

    private static List<Path> listFiles(Path root, String suffix) {
        if (!Files.isDirectory(root)) return List.of();
        try (Stream<Path> s = Files.walk(root)) {
            return s.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(suffix))
                    .sorted()
                    .toList();
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
