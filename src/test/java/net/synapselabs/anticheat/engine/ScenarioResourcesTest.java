package net.synapselabs.anticheat.engine;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Loads the shared combat fixtures in {@code src/test/resources/scenarios/} and enforces the schema
 * contract on them: canonical feature keys, valid verdict, matching schema version, clean assembly.
 *
 * <p>These are the SAME files the Python harness ({@code run_scenarios.py}) consumes, so both sides
 * are held to identical cases. Once the Risk Engine lands in Phase 1, this class also asserts each
 * fixture's {@code expected_verdict}.
 */
class ScenarioResourcesTest {

    private static final Set<String> VALID_VERDICTS = Set.of("LEGIT", "SUSPICIOUS", "CHEAT");
    private static final Set<String> CANONICAL = Set.of(FeatureSchema.NAMES);

    private List<Path> scenarioFiles() throws Exception {
        URL dir = getClass().getClassLoader().getResource("scenarios");
        assertNotNull(dir, "scenarios/ resource folder must be on the test classpath");
        Path base = Paths.get(dir.toURI());
        try (Stream<Path> s = Files.list(base)) {
            return s.filter(p -> p.toString().endsWith(".json"))
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    @Test
    void fixturesExist() throws Exception {
        assertFalse(scenarioFiles().isEmpty(), "expected at least one scenario fixture");
    }

    @Test
    void everyFixtureIsSchemaConformant() throws Exception {
        List<String> problems = new ArrayList<>();
        for (Path f : scenarioFiles()) {
            String name = f.getFileName().toString();
            JsonObject sc;
            try {
                sc = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (RuntimeException | IOException e) {
                problems.add(name + ": parse error " + e.getMessage());
                continue;
            }

            if (!sc.has("id")) problems.add(name + ": missing id");

            String verdict = sc.has("expected_verdict") ? sc.get("expected_verdict").getAsString() : null;
            if (!VALID_VERDICTS.contains(verdict)) {
                problems.add(name + ": bad expected_verdict " + verdict);
            }

            String ver = sc.has("feature_schema_version") ? sc.get("feature_schema_version").getAsString() : null;
            if (!FeatureSchema.VERSION.equals(ver)) {
                problems.add(name + ": schema version " + ver + " != " + FeatureSchema.VERSION);
            }

            // Every feature key must be canonical, and the assembled vector must validate.
            java.util.HashMap<String, Float> feats = new java.util.HashMap<>();
            if (sc.has("features")) {
                for (Map.Entry<String, com.google.gson.JsonElement> e : sc.getAsJsonObject("features").entrySet()) {
                    if (!CANONICAL.contains(e.getKey())) {
                        problems.add(name + ": unknown feature key '" + e.getKey() + "'");
                    }
                    feats.put(e.getKey(), e.getValue().getAsFloat());
                }
            }
            String verr = FeatureSchema.validate(FeatureSchema.assemble(feats));
            if (verr != null) problems.add(name + ": assemble invalid — " + verr);
        }
        assertTrue(problems.isEmpty(), "scenario contract violations:\n  " + String.join("\n  ", problems));
    }

    @Test
    void headlineFalsePositiveFixtureIsPresentAndLegit() throws Exception {
        for (Path f : scenarioFiles()) {
            JsonObject sc = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
            if ("corner_360_crit_legit".equals(sc.get("id").getAsString())) {
                assertEquals("LEGIT", sc.get("expected_verdict").getAsString(),
                        "the 360°-corner-crit regression case must be labelled LEGIT");
                return;
            }
        }
        fail("regression fixture 'corner_360_crit_legit' is missing");
    }
}
