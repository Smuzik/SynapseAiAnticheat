package net.synapselabs.anticheat.engine;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Computes the multiplicative context modifiers applied to each signal's weight, and the
 * lag-compensated reach excess. Pure logic — no Bukkit dependency — so it is unit-tested against the
 * same scenarios as the Python prototype.
 *
 * <p>Modifier factors &lt; 1 REDUCE suspicion (a legit explanation exists); factors &gt; 1 AMPLIFY it.
 */
public final class ContextEngine {

    // Modifier factors (mirror risk_engine.py).
    public static final double M_CORNER = 0.25;
    public static final double M_NEAR_WALL = 0.50;
    public static final double M_KNOCKBACK = 0.40;
    public static final double M_HIGH_PING = 0.70;
    public static final double M_CRIT = 0.55;
    public static final double M_TARGET_SWITCH = 0.50;
    public static final double M_HUMAN_DECEL = 0.50;
    public static final double M_REPEATED = 1.50;   // the one amplifier

    /**
     * Multi-sample consistency gate for the two "pattern" signals ({@link SignalType#AIM_CONSISTENCY},
     * {@link SignalType#KINEMATIC_ROBOTIC}). On a single/unconfirmed event these are only WEAK evidence:
     * a skilled human can land one precise, still-accelerating flick (near a corner, mid-360°) that looks
     * exactly like a killaura hit. Real killaura shows the SAME signature repeated across the window — the
     * tracker sets {@code repeatedPattern} once {@code roboticStreak >= REPEATED_PATTERN_MIN}. Until that
     * consistency is confirmed, these two signals are scaled down so a handful of isolated legit
     * precise/accelerating flicks cannot stack through the additive {@link RiskAccumulator} to CHEAT.
     * When {@code repeatedPattern} IS set the {@link #M_REPEATED} amplifier applies instead. This is a
     * temporal-consistency gate, NOT a threshold change — hard structural signals (snap/hitbox/reach) are
     * unaffected and still count in full on a single event.
     */
    public static final double M_UNCONFIRMED = 0.35;

    public static final int HIGH_PING_MS = 100;
    public static final double DECEL_THRESHOLD = -20.0;

    // Lag compensation (scalar prototype; the runtime cross-checks against a real victim position
    // history buffer — see LagCompensator — this is the fallback / unit-test model).
    public static final double BASE_REACH = 3.0;
    public static final double KB_SPEED = 6.0;      // blocks/sec a knocked-back victim can travel
    public static final double PING_JITTER = 1.0;   // blocks/sec of positional uncertainty per sec of ping

    private ContextEngine() {}

    /** Blocks by which a reach reading exceeds what lag + knockback can legitimately explain (&ge; 0). */
    public static double lagCompensatedReachExcess(double value, CombatContext ctx) {
        double pingSec = ctx.pingMs() / 1000.0;
        double allowance = BASE_REACH;
        if (ctx.victimKnockback()) allowance += KB_SPEED * pingSec;
        allowance += PING_JITTER * pingSec;
        return Math.max(0.0, value - allowance);
    }

    /** Dynamic confidence for a REACH signal from its lag-compensated excess (0.05 .. 1.0). */
    public static double reachConfidence(double value, CombatContext ctx) {
        double excess = lagCompensatedReachExcess(value, ctx);
        return excess <= 0.0 ? 0.05 : Math.min(1.0, 0.2 + excess);
    }

    /** The multiplicative modifiers that apply to a given signal, in a stable order for explainability. */
    public static Map<String, Double> modifiersFor(SignalType type, CombatContext ctx) {
        Map<String, Double> mods = new LinkedHashMap<>();
        boolean snapLike = (type == SignalType.HARD_SNAP || type == SignalType.HITBOX_MISS);
        boolean reachLike = snapLike || type == SignalType.REACH;

        if (snapLike && ctx.inCorner()) {
            mods.put("corner", M_CORNER);
        } else if (snapLike && ctx.nearWall()) {
            mods.put("near_wall", M_NEAR_WALL);
        }

        if (reachLike && ctx.victimKnockback()) {
            mods.put("knockback", M_KNOCKBACK);
        }
        if (reachLike && ctx.pingMs() > HIGH_PING_MS) {
            mods.put("high_ping", M_HIGH_PING);
        }
        if ((type == SignalType.HARD_SNAP || type == SignalType.KINEMATIC_ROBOTIC) && ctx.isCrit()) {
            mods.put("crit", M_CRIT);
        }
        if (snapLike && ctx.targetSwitch()) {
            mods.put("target_switch", M_TARGET_SWITCH);
        }
        if ((type == SignalType.HARD_SNAP || type == SignalType.KINEMATIC_ROBOTIC)
                && ctx.yawAccel() < DECEL_THRESHOLD) {
            mods.put("human_decel", M_HUMAN_DECEL);
        }

        // Temporal-consistency gate for the two soft "pattern" signals. A single precise flick
        // (AIM_CONSISTENCY) or a single still-accelerating snap (KINEMATIC_ROBOTIC) is weak
        // evidence on its own — a skilled human produces both occasionally. Until the tracker
        // confirms the SAME signature repeated across the window (repeatedPattern), scale these
        // down so isolated legit flicks cannot stack through the additive RiskAccumulator to
        // CHEAT. Mutually exclusive with the repeated_pattern amplifier below (which requires
        // repeatedPattern == true), so a confirmed streak is unaffected. Hard structural signals
        // (snap/hitbox/reach) are NOT gated — they count in full on a single event.
        boolean patternSignal =
                (type == SignalType.AIM_CONSISTENCY || type == SignalType.KINEMATIC_ROBOTIC);
        if (patternSignal && !ctx.repeatedPattern()) {
            mods.put("unconfirmed_pattern", M_UNCONFIRMED);
        }

        if (ctx.repeatedPattern()) {
            mods.put("repeated_pattern", M_REPEATED);
        }
        return mods;
    }
}
