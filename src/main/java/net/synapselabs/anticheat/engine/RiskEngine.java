package net.synapselabs.anticheat.engine;

import net.synapselabs.anticheat.engine.RiskAssessment.Contribution;
import net.synapselabs.anticheat.engine.RiskAssessment.Verdict;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The Risk Engine: {@code signal -> base weight -> context modifiers (multiplicative) -> confidence ->
 * temporal aggregation -> risk score}.
 *
 * <p>Two entry points:
 * <ul>
 *   <li>{@link #assess(List, CombatContext, double, double)} — runtime: you supply the already-aggregated
 *       (temporally decayed) risk from {@link RiskAccumulator}; the engine attaches the per-signal
 *       breakdown and derives the verdict.</li>
 *   <li>{@link #scoreScenario(List, CombatContext, double)} — offline/tests: folds temporal aggregation
 *       in (a repeated pattern replays through the decay accumulator), reproducing the Python reference
 *       numbers exactly.</li>
 * </ul>
 *
 * <p>The AI model is added as ONE contribution weighted by {@link #MODEL_TRUST}; it can never by itself
 * push a player to CHEAT while its trust is low. That is deliberate (see the audit).
 */
public final class RiskEngine {

    public static final double MODEL_TRUST = 0.5;
    public static final double MODEL_SCALE = 30.0;
    public static final double RISK_CLAMP = 100.0;

    public static final double DEFAULT_SUSPICIOUS = 25.0;
    public static final double DEFAULT_CHEAT = 60.0;

    /** Temporal decay applied per accumulated event (also used by {@link RiskAccumulator}). */
    public static final double DECAY = 0.85;
    private static final int REPEATED_REPLAY = 6;

    private final double suspiciousThreshold;
    private final double cheatThreshold;

    public RiskEngine() {
        this(DEFAULT_SUSPICIOUS, DEFAULT_CHEAT);
    }

    public RiskEngine(double suspiciousThreshold, double cheatThreshold) {
        this.suspiciousThreshold = suspiciousThreshold;
        this.cheatThreshold = cheatThreshold;
    }

    /** Per-signal contributions for a single event (AI model always appended as one contribution). */
    public List<Contribution> contributions(List<Signal> signals, CombatContext ctx, double modelPCheat) {
        List<Contribution> out = new ArrayList<>();
        for (Signal s : signals) {
            if (s.type() == SignalType.AI_MODEL) continue; // AI added once, below
            double base = s.baseWeight();
            double conf = (s.type() == SignalType.REACH)
                    ? ContextEngine.reachConfidence(s.value(), ctx)
                    : s.confidence();
            Map<String, Double> mods = ContextEngine.modifiersFor(s.type(), ctx);
            double factor = 1.0;
            for (double f : mods.values()) factor *= f;
            out.add(new Contribution(s.type(), base, conf, mods, base * conf * factor));
        }
        double aiBase = modelPCheat * MODEL_SCALE;
        out.add(new Contribution(SignalType.AI_MODEL, aiBase, MODEL_TRUST, Map.of(), aiBase * MODEL_TRUST));
        return out;
    }

    public double eventRisk(List<Contribution> contributions) {
        double sum = 0.0;
        for (Contribution c : contributions) sum += c.contribution();
        return sum;
    }

    public Verdict verdictFor(double risk) {
        if (risk >= cheatThreshold) return Verdict.CHEAT;
        if (risk >= suspiciousThreshold) return Verdict.SUSPICIOUS;
        return Verdict.LEGIT;
    }

    /** Runtime assessment from an already-aggregated risk (from {@link RiskAccumulator}). */
    public RiskAssessment assess(List<Signal> signals, CombatContext ctx, double modelPCheat, double aggregatedRisk) {
        List<Contribution> contribs = contributions(signals, ctx, modelPCheat);
        double ev = eventRisk(contribs);
        double risk = clamp(aggregatedRisk);
        Verdict verdict = verdictFor(risk);
        // Core False-Positive Invariant: If there are ZERO structural signals and low model confidence (< 0.80),
        // the player stays LEGIT.
        if (signals.isEmpty() && modelPCheat < 0.80 && verdict != Verdict.LEGIT) {
            verdict = Verdict.LEGIT;
            risk = Math.min(risk, 15.0);
        }
        return new RiskAssessment(verdict, risk, ev, contribs);
    }

    /** Offline/test scorer: temporal aggregation folded in. Reproduces risk_engine.py exactly. */
    public RiskAssessment scoreScenario(List<Signal> signals, CombatContext ctx, double modelPCheat) {
        List<Contribution> contribs = contributions(signals, ctx, modelPCheat);
        double ev = eventRisk(contribs);
        double risk = ctx.repeatedPattern() ? aggregateRepeated(ev) : ev;
        risk = clamp(risk);
        return new RiskAssessment(verdictFor(risk), risk, ev, contribs);
    }

    /** Replay a repeated pattern through the decay accumulator (temporal proxy for offline scoring). */
    public static double aggregateRepeated(double eventRisk) {
        double acc = 0.0;
        for (int i = 0; i < REPEATED_REPLAY; i++) acc = acc * DECAY + eventRisk;
        return acc;
    }

    public static double clamp(double risk) {
        return Math.max(0.0, Math.min(RISK_CLAMP, risk));
    }

    /**
     * Appends the derived "Aim" and "Rotation" signals from raw kinematics, matching the Python
     * prototype's thresholds. Called by both the runtime collector and the tests.
     *
     * <p>Bug #5 fix: added {@code distance} parameter. At close range (&lt; 2 blocks) any player
     * naturally has a tiny angle offset, so AIM_CONSISTENCY is suppressed. Also tightened the
     * angle threshold from 1.5° to 1.0° to reduce false positives from micro-mouse-adjustments.
     */
    public static void addDerivedSignals(List<Signal> signals,
                                         float angleOffsetDeg, float raycastAngleError,
                                         float yawDelta5t, float yawAccel,
                                         double distance) {
        // Bug #5: at close range (< 2 blocks) a small angle offset is natural — don't flag it.
        // Also tightened from 1.5° to 1.0° to tolerate normal mouse micro-adjustments.
        if (angleOffsetDeg < 1.0f && raycastAngleError < 2.0f && distance >= 2.0) {
            signals.add(Signal.of(SignalType.AIM_CONSISTENCY, SignalType.AIM_CONSISTENCY.defaultConfidence(),
                    angleOffsetDeg, "aim offset " + angleOffsetDeg + "°"));
        }
        if (Math.abs(yawDelta5t) > 100.0f && yawAccel > 40.0f) {
            signals.add(Signal.of(SignalType.KINEMATIC_ROBOTIC, SignalType.KINEMATIC_ROBOTIC.defaultConfidence(),
                    yawAccel, "no deceleration (accel " + yawAccel + ")"));
        }
    }
}
