package net.synapselabs.anticheat.lang;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 3c localization guard.
 *
 * <p>Verifies that the English and Russian message bundles stay structurally in lock-step and that
 * every translation key the Java code asks for actually exists in both files. This is intentionally
 * dependency-free: {@code purpur-api} is a {@code compileOnly} dependency, so SnakeYAML / Bukkit's
 * {@code YamlConfiguration} are not on the test classpath. The message files use a tiny, fixed YAML
 * subset (two indent levels, double-quoted scalars, {@code #} comments), so a small hand-written
 * line parser is sufficient and avoids pulling a YAML library into the test scope.</p>
 */
class LocalizationParityTest {

    private static final String EN_FILE = "messages_en.yml";
    private static final String RU_FILE = "messages_ru.yml";

    /** Placeholder tokens look like {@code {player}}, {@code {discord_url}}, {@code {score}}. */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-zA-Z0-9_]+)\\}");

    /** A message key is dotted lowercase-ish text; anything with '/', ':' or spaces is not a key. */
    private static final Pattern KEY_LITERAL = Pattern.compile("[A-Za-z0-9_.]+");

    /**
     * Keys that are referenced dynamically (never as a string literal next to getMessage/getRaw)
     * and therefore cannot be discovered by the source scanner. ThreatState.messageKey() yields
     * exactly these four values, so they must exist in both bundles.
     */
    private static final List<String> DYNAMIC_KEYS = List.of(
            "threat_state.clean",
            "threat_state.suspicious",
            "threat_state.high_confidence",
            "threat_state.confirmed"
    );

    @Test
    void bothBundlesLoadAndAreNonEmpty() throws Exception {
        Bundle en = parse(loadMessageLines(EN_FILE));
        Bundle ru = parse(loadMessageLines(RU_FILE));
        assertFalse(en.values.isEmpty(), EN_FILE + " parsed to zero keys");
        assertFalse(ru.values.isEmpty(), RU_FILE + " parsed to zero keys");
    }

    @Test
    void neitherBundleHasDuplicateKeys() throws Exception {
        Bundle en = parse(loadMessageLines(EN_FILE));
        Bundle ru = parse(loadMessageLines(RU_FILE));
        List<String> problems = new ArrayList<>();
        if (!en.duplicates.isEmpty()) problems.add("Duplicate keys in " + EN_FILE + ": " + en.duplicates);
        if (!ru.duplicates.isEmpty()) problems.add("Duplicate keys in " + RU_FILE + ": " + ru.duplicates);
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void keySetsAreIdentical() throws Exception {
        Bundle en = parse(loadMessageLines(EN_FILE));
        Bundle ru = parse(loadMessageLines(RU_FILE));

        Set<String> onlyEn = new TreeSet<>(en.values.keySet());
        onlyEn.removeAll(ru.values.keySet());
        Set<String> onlyRu = new TreeSet<>(ru.values.keySet());
        onlyRu.removeAll(en.values.keySet());

        List<String> problems = new ArrayList<>();
        if (!onlyEn.isEmpty()) problems.add("Keys present in EN but missing in RU: " + onlyEn);
        if (!onlyRu.isEmpty()) problems.add("Keys present in RU but missing in EN: " + onlyRu);
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void placeholdersMatchBetweenLanguages() throws Exception {
        Bundle en = parse(loadMessageLines(EN_FILE));
        Bundle ru = parse(loadMessageLines(RU_FILE));

        Set<String> shared = new TreeSet<>(en.values.keySet());
        shared.retainAll(ru.values.keySet());

        List<String> problems = new ArrayList<>();
        for (String key : shared) {
            Set<String> pe = placeholders(en.values.get(key));
            Set<String> pr = placeholders(ru.values.get(key));
            if (!pe.equals(pr)) {
                problems.add(key + " -> EN" + pe + " vs RU" + pr);
            }
        }
        assertTrue(problems.isEmpty(), "Placeholder set mismatches between EN and RU:\n  "
                + String.join("\n  ", problems));
    }

    @Test
    void everyJavaReferencedKeyResolvesInBothBundles() throws Exception {
        Bundle en = parse(loadMessageLines(EN_FILE));
        Bundle ru = parse(loadMessageLines(RU_FILE));

        Path javaDir = locateDir("src/main/java");
        Assumptions.assumeTrue(javaDir != null,
                "src/main/java not found on filesystem; skipping Java key-reference check");

        Set<String> referenced = new TreeSet<>();
        try (Stream<Path> paths = Files.walk(javaDir)) {
            List<Path> javaFiles = paths
                    .filter(p -> p.toString().endsWith(".java"))
                    .collect(Collectors.toList());
            for (Path jf : javaFiles) {
                referenced.addAll(extractReferencedKeys(Files.readString(jf, StandardCharsets.UTF_8)));
            }
        }
        referenced.addAll(DYNAMIC_KEYS);

        List<String> problems = new ArrayList<>();
        for (String key : referenced) {
            boolean inEn = en.values.containsKey(key);
            boolean inRu = ru.values.containsKey(key);
            if (!inEn || !inRu) {
                String where = (!inEn && !inRu) ? "EN and RU" : (!inEn ? "EN" : "RU");
                problems.add(key + " -> missing in " + where);
            }
        }
        assertTrue(problems.isEmpty(), "Java-referenced message keys that do not resolve:\n  "
                + String.join("\n  ", problems));
    }

    // ------------------------------------------------------------------
    // YAML (tiny subset) parsing
    // ------------------------------------------------------------------

    /** Parsed bundle: flattened dotted key -> raw value, plus any duplicate keys detected. */
    private static final class Bundle {
        final Map<String, String> values;
        final List<String> duplicates;

        Bundle(Map<String, String> values, List<String> duplicates) {
            this.values = values;
            this.duplicates = duplicates;
        }
    }

    /**
     * Parses the fixed YAML subset used by the message files into flattened dotted keys.
     * Handles arbitrary nesting via an indent stack; comments ({@code #}) and blank lines are
     * skipped. Only the first ':' separates key from value, so inner ':' inside a quoted value
     * (URLs, "Discord: ...") is preserved.
     */
    private static Bundle parse(List<String> lines) {
        Map<String, String> values = new LinkedHashMap<>();
        List<String> duplicates = new ArrayList<>();
        Deque<Integer> indentStack = new ArrayDeque<>();
        Deque<String> nameStack = new ArrayDeque<>();

        for (String raw : lines) {
            if (raw.trim().isEmpty()) continue;

            int indent = 0;
            while (indent < raw.length() && raw.charAt(indent) == ' ') indent++;
            String content = raw.substring(indent);
            if (content.startsWith("#")) continue;

            int colon = content.indexOf(':');
            if (colon < 0) continue;

            String key = content.substring(0, colon).trim();
            String rest = content.substring(colon + 1).trim();

            // Unwind to the parent whose indent is strictly smaller than this line's.
            while (!indentStack.isEmpty() && indentStack.peek() >= indent) {
                indentStack.pop();
                nameStack.pop();
            }

            String full;
            if (nameStack.isEmpty()) {
                full = key;
            } else {
                List<String> parts = new ArrayList<>(nameStack); // most-recent first
                Collections.reverse(parts);                      // root .. parent
                parts.add(key);
                full = String.join(".", parts);
            }

            if (rest.isEmpty()) {
                // Section header: descend.
                indentStack.push(indent);
                nameStack.push(key);
            } else {
                // Leaf value.
                if (values.containsKey(full)) duplicates.add(full);
                values.put(full, stripQuotes(rest));
            }
        }
        return new Bundle(values, duplicates);
    }

    private static String stripQuotes(String s) {
        if (s.length() >= 2 && s.charAt(0) == '"' && s.charAt(s.length() - 1) == '"') {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    private static Set<String> placeholders(String value) {
        Set<String> set = new TreeSet<>();
        Matcher m = PLACEHOLDER.matcher(value);
        while (m.find()) set.add(m.group(1));
        return set;
    }

    // ------------------------------------------------------------------
    // Java source scanning
    // ------------------------------------------------------------------

    /**
     * Extracts message keys referenced via {@code getMessage(...)} / {@code getRaw(...)}.
     *
     * <p>Only string literals that sit at paren-depth 0 of the <em>first</em> argument are treated
     * as keys. This correctly:</p>
     * <ul>
     *   <li>captures both branches of a ternary key, e.g.
     *       {@code getRaw(active ? "a.b" : "c.d", lang)};</li>
     *   <li>ignores placeholder-name literals (2nd, 4th, ... arguments);</li>
     *   <li>ignores config keys nested inside a call, e.g. the
     *       {@code "discord.webhook.enabled"} inside
     *       {@code getRaw(getConfig().getBoolean("discord.webhook.enabled", false) ? "x" : "y", ...)};</li>
     *   <li>ignores dynamic keys with no literal, e.g.
     *       {@code getRaw(profile.getThreatState().messageKey(), lang)} and
     *       {@code event.getMessage()}.</li>
     * </ul>
     */
    private static Set<String> extractReferencedKeys(String source) {
        Set<String> keys = new HashSet<>();
        for (String method : new String[]{"getMessage(", "getRaw("}) {
            int idx = 0;
            while ((idx = source.indexOf(method, idx)) >= 0) {
                int p = idx + method.length();
                int depth = 0;
                boolean done = false;
                while (p < source.length() && !done) {
                    char c = source.charAt(p);
                    if (c == '"') {
                        StringBuilder sb = new StringBuilder();
                        p++; // past opening quote
                        while (p < source.length()) {
                            char d = source.charAt(p);
                            if (d == '\\') { p += 2; continue; } // skip escape sequence
                            if (d == '"') break;                 // closing quote
                            sb.append(d);
                            p++;
                        }
                        if (depth == 0) {
                            String lit = sb.toString();
                            if (KEY_LITERAL.matcher(lit).matches()) keys.add(lit);
                        }
                        p++;       // past closing quote
                        continue;  // skip trailing p++ below
                    } else if (c == '(') {
                        depth++;
                    } else if (c == ')') {
                        if (depth == 0) done = true; // end of the getMessage/getRaw call
                        else depth--;
                    } else if (c == ',') {
                        if (depth == 0) done = true; // end of the first argument
                    }
                    p++;
                }
                idx = p;
            }
        }
        return keys;
    }

    // ------------------------------------------------------------------
    // Resource / filesystem location
    // ------------------------------------------------------------------

    private List<String> loadMessageLines(String fileName) throws Exception {
        // Primary: classpath (Gradle places src/main/resources on the test runtime classpath).
        InputStream in = getClass().getClassLoader().getResourceAsStream(fileName);
        if (in != null) {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                List<String> out = new ArrayList<>();
                String line;
                while ((line = br.readLine()) != null) out.add(line);
                return out;
            }
        }
        // Fallback: read straight from the source tree.
        Path fs = locateFile("src/main/resources/" + fileName);
        if (fs != null) return Files.readAllLines(fs, StandardCharsets.UTF_8);
        throw new IllegalStateException(fileName + " not found on classpath or under src/main/resources");
    }

    private static Path locateFile(String relative) {
        Path dir = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 8 && dir != null; i++) {
            Path candidate = dir.resolve(relative);
            if (Files.isRegularFile(candidate)) return candidate;
            dir = dir.getParent();
        }
        return null;
    }

    private static Path locateDir(String relative) {
        Path dir = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 8 && dir != null; i++) {
            Path candidate = dir.resolve(relative);
            if (Files.isDirectory(candidate)) return candidate;
            dir = dir.getParent();
        }
        return null;
    }
}
