package net.synapselabs.anticheat.engine;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.synapselabs.anticheat.engine.RiskAssessment.Contribution;
import net.synapselabs.anticheat.engine.RiskAssessment.Verdict;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Risk Engine's behavioural contract. Every number asserted here is reproduced from the Python
 * reference ({@code risk_engine.py}, run against the SAME fixtures), so a Java/Python divergence fails
 * the build. These tests run with {@code modelPCheat = 0} — i.e. they prove the engine is correct with
 * the AI switched OFF, which is the whole point: the decision must be sound on structural signals + context
 * alone, and the model is only ever a minor add-on.
 */
class RiskEngineTest {

    private static final double EPS = 1e-6;
    private final RiskEngine engine = new RiskEngine();

    // ---- fixture loading ---------------------------------------------------

    private JsonObject load(String id) throws Exception {
        URL url = getClass().getClassLoader().getResource("scenarios/" + id + ".json");
        assertNotNull(url, "fixture " + id + " must be on the test classpath");
        return JsonParser.parseString(Files.readString(Paths.get(url.toURI()), StandardCharsets.UTF_8))
                .getAsJsonObject();
    }

    private List<Path> allFixtures() throws Exception {
        URL dir = getClass().getClassLoader().getResource("scenarios");
        assertNotNull(dir, "scenarios/ must be on the test classpath");
        try (Stream<Path> s = Files.list(Paths.get(dir.toURI()))) {
            return s.filter(p -> p.toString().endsWith(".json")).sorted().collect(Collectors.toList());
        }
    }

    private static boolean bool(JsonObject o, String k) {
        return o.has(k) && o.get(k).getAsBoolean();
    }

    private static float feat(JsonObject f, String k, float dflt) {
        return f.has(k) ? f.get(k).getAsFloat() : dflt;
    }

    private CombatContext contextOf(JsonObject sc) {
        JsonObject c = sc.getAsJsonObject("context");
        JsonObject f = sc.getAsJsonObject("features");
        return CombatContext.builder()
                .inCorner(bool(c, "in_corner"))
                .nearWall(bool(c, "near_wall"))
                .victimKnockback(bool(c, "victim_knockback"))
                .pingMs(c.has("attacker_ping_ms") ? c.get("attacker_ping_ms").getAsInt() : 0)
                .isCrit(bool(c, "is_crit"))
                .targetSwitch(bool(c, "target_switch"))
                .repeatedPattern(bool(c, "repeated_pattern"))
                .yawAccel(f.has("yaw_accel") ? f.get("yaw_accel").getAsDouble() : 0.0)
                .build();
    }

    /** Rebuilds the signal list exactly as {@code _fired_signals} does in the Python reference. */
    private List<Signal> signalsOf(JsonObject sc) {
        JsonObject f = sc.getAsJsonObject("features");
        List<Signal> signals = new ArrayList<>();
        if (sc.has("signals")) {
            for (JsonElement el : sc.getAsJsonArray("signals")) {
                JsonObject s = el.getAsJsonObject();
                if (s.has("fired") && !s.get("fired").getAsBoolean()) continue;
                SignalType type = SignalType.valueOf(s.get("type").getAsString());
                double value = s.has("value") ? s.get("value").getAsDouble() : Double.NaN;
                signals.add(Signal.of(type, type.defaultConfidence(), value, null));
            }
        }
        RiskEngine.addDerivedSignals(signals,
                feat(f, "angle_offset_deg", 99f),
                feat(f, "raycast_angle_error", 99f),
                feat(f, "yaw_delta_5t", 0f),
                feat(f, "yaw_accel", 0f),
                3.0);
        return signals;
    }

    private Contribution contribFor(List<Contribution> cs, SignalType t) {
        return cs.stream().filter(c -> c.signal() == t).findFirst()
                .orElseThrow(() -> new AssertionError("no contribution for " + t));
    }

    // ---- the headline regression case -------------------------------------

    @Test
    void cornerThreeSixtyCritResolvesToLegit_withAiOff() throws Exception {
        JsonObject sc = load("corner_360_crit_legit");
        RiskAssessment r = engine.scoreScenario(signalsOf(sc), contextOf(sc), 0.0);

        assertEquals(Verdict.LEGIT, r.verdict(),
                "the 360°-corner-crit false positive MUST resolve to LEGIT on signals+context alone");
        assertFalse(r.isActionable(), "a legit flick must never be actionable");

        // Per-signal breakdown reproduced from risk_engine.py.
        Contribution snap = contribFor(r.contributions(), SignalType.HARD_SNAP);
        Contribution miss = contribFor(r.contributions(), SignalType.HITBOX_MISS);
        assertEquals(0.259875, snap.contribution(), EPS, "HARD_SNAP crushed by corner+kb+ping+crit+decel");
        assertEquals(0.476, miss.contribution(), EPS, "HITBOX_MISS crushed by corner+kb+ping");
        assertEquals(0.735875, r.eventRisk(), EPS, "corner event risk");
        assertEquals(0.735875, r.risk(), EPS, "no repeated pattern -> risk == event risk");
    }

    // ---- the blatant cheat case -------------------------------------------

    @Test
    void blatantKillauraReachesCheat_withAiOff() throws Exception {
        JsonObject sc = load("blatant_killaura_cheat");
        List<Signal> signals = signalsOf(sc);
        CombatContext ctx = contextOf(sc);

        // Aim + Rotation signals are DERIVED from the (signed) kinematics, not present in the JSON.
        assertTrue(signals.stream().anyMatch(s -> s.type() == SignalType.AIM_CONSISTENCY),
                "superhuman aim must derive an AIM_CONSISTENCY signal");
        assertTrue(signals.stream().anyMatch(s -> s.type() == SignalType.KINEMATIC_ROBOTIC),
                "non-decelerating 360 must derive a KINEMATIC_ROBOTIC signal");

        RiskAssessment r = engine.scoreScenario(signals, ctx, 0.0);
        assertEquals(35.025, r.eventRisk(), EPS, "single-event risk before temporal aggregation");
        assertEquals(100.0, r.risk(), EPS, "repeated robotic snaps aggregate past the clamp");
        assertEquals(Verdict.CHEAT, r.verdict());
        assertTrue(r.isActionable());

        // Individual contributions (amplified by target_switch and repeated_pattern).
        assertEquals(10.125, contribFor(r.contributions(), SignalType.HARD_SNAP).contribution(), EPS);
        assertEquals(14.4, contribFor(r.contributions(), SignalType.AIM_CONSISTENCY).contribution(), EPS);
        assertEquals(10.5, contribFor(r.contributions(), SignalType.KINEMATIC_ROBOTIC).contribution(), EPS);
    }

    // ---- every fixture, driven from its own expected_verdict --------------

    @Test
    void everyFixtureMatchesItsExpectedVerdict_withAiOff() throws Exception {
        List<String> failures = new ArrayList<>();
        for (Path f : allFixtures()) {
            JsonObject sc = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
            Verdict expected = Verdict.valueOf(sc.get("expected_verdict").getAsString());
            RiskAssessment r = engine.scoreScenario(signalsOf(sc), contextOf(sc), 0.0);
            if (r.verdict() != expected) {
                failures.add(sc.get("id").getAsString() + ": expected " + expected
                        + " got " + r.verdict() + " (risk=" + r.risk() + ")");
            }
        }
        assertTrue(failures.isEmpty(), "verdict mismatches:\n  " + String.join("\n  ", failures));
    }

    // ---- the AI can never be the gate -------------------------------------

    @Test
    void aiModelAloneCannotReachCheatOrSuspicious() {
        CombatContext neutral = CombatContext.builder().build();
        // Maximum possible model output, no structural signals at all.
        RiskAssessment r = engine.scoreScenario(List.of(), neutral, 1.0);
        assertEquals(15.0, r.risk(), EPS, "p=1.0 -> 1.0 * MODEL_SCALE * MODEL_TRUST = 15");
        assertEquals(Verdict.LEGIT, r.verdict(), "even a maximally confident model alone stays below SUSPICIOUS");
        assertFalse(r.isActionable(), "AI is one signal, never the gate");
    }

    // ---- lag compensation --------------------------------------------------

    @Test
    void lagCompensationNeutralizesReachUnderPingAndKnockback() {
        CombatContext lag = CombatContext.builder().victimKnockback(true).pingMs(150).build();
        // 3.42m at 150ms ping while the victim is knocked back is fully explained -> no excess.
        assertEquals(0.0, ContextEngine.lagCompensatedReachExcess(3.42, lag), EPS);
        assertEquals(0.05, ContextEngine.reachConfidence(3.42, lag), EPS, "no excess -> floor confidence");

        // The SAME 3.42m with no lag and no knockback IS real reach -> genuine excess and high confidence.
        CombatContext clean = CombatContext.builder().build();
        assertEquals(0.42, ContextEngine.lagCompensatedReachExcess(3.42, clean), EPS);
        assertEquals(0.62, ContextEngine.reachConfidence(3.42, clean), EPS);
    }

    // ---- temporal accumulator (runtime model) -----------------------------

    @Test
    void accumulatorSingleEventEqualsEventRisk() {
        RiskAccumulator acc = new RiskAccumulator();
        assertEquals(35.025, acc.observe(35.025, 1_000L), EPS, "first event: nothing to decay");
    }

    @Test
    void accumulatorDecaysByHalfOverHalfLife() {
        RiskAccumulator acc = new RiskAccumulator(RiskAccumulator.DEFAULT_HALF_LIFE_MILLIS);
        acc.observe(50.0, 1_000L);
        double after = acc.current(1_000L + RiskAccumulator.DEFAULT_HALF_LIFE_MILLIS);
        assertEquals(25.0, after, 1e-9, "risk halves over one half-life of inactivity");
    }

    @Test
    void accumulatorStacksRepeatedSnapsPastCheatThreshold() {
        RiskAccumulator acc = new RiskAccumulator();
        acc.observe(35.025, 1_000L);
        double stacked = acc.observe(35.025, 1_100L); // 100ms later: negligible decay
        assertTrue(stacked > RiskEngine.DEFAULT_CHEAT,
                "two robotic snaps 100ms apart stack past the CHEAT threshold, got " + stacked);
        assertEquals(Verdict.CHEAT, engine.verdictFor(stacked));
    }

    @Test
    void aiContributionIsStrictlyNonNegativeAndNeverReducesRisk() {
        // Base hard signals that yield 55.0 risk
        List<Signal> signals = List.of(
            Signal.of(SignalType.HARD_SNAP, 1.0, 55.0, "hard snap"),
            Signal.of(SignalType.REACH, 1.0, 3.4, "reach")
        );
        CombatContext cleanCtx = CombatContext.builder().pingMs(20).build();

        double riskWithAiOff = engine.eventRisk(engine.contributions(signals, cleanCtx, 0.0));
        assertTrue(riskWithAiOff > 0.0, "Base hard signals produce positive risk");

        // Test with low AI probability p=0.12 (e.g. Rockstar uncalibrated sample)
        double riskWithLowP = engine.eventRisk(engine.contributions(signals, cleanCtx, 0.12));
        assertTrue(riskWithLowP >= riskWithAiOff, "AI score p=0.12 must NEVER reduce risk below AI=0.0 level (got " + riskWithLowP + " vs " + riskWithAiOff + ")");
        assertEquals(riskWithAiOff + (0.12 * RiskEngine.MODEL_SCALE * RiskEngine.MODEL_TRUST), riskWithLowP, EPS,
                "AI score is strictly additive and non-negative");

        // Test across the full range [0.0, 1.0]
        for (double p = 0.0; p <= 1.0; p += 0.05) {
            double risk = engine.eventRisk(engine.contributions(signals, cleanCtx, p));
            assertTrue(risk >= riskWithAiOff, "Risk at p=" + p + " must be >= base risk");
        }
    }
}
