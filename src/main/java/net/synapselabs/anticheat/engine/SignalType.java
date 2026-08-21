package net.synapselabs.anticheat.engine;

/**
 * The categories of evidence the {@link RiskEngine} aggregates. Each is ONE signal among many — never
 * a verdict on its own. This mirrors the signal families surfaced in the admin dashboard
 * (Aim / Rotation / Combat / Hard checks / AI).
 *
 * <p>Base weight and default confidence are the reference values proven in the Python prototype
 * ({@code freecam-pvp-collector/python/risk_engine.py}); the Java unit tests assert parity.
 */
public enum SignalType {
    /** Hard-check: impossible angular velocity + acceleration, ray misses, tiny final angle. */
    HARD_SNAP(15.0, 0.90),
    /** Hard-check: line of sight clearly off the target's hitbox. */
    HITBOX_MISS(8.0, 0.85),
    /** Hard-check: reach beyond what lag compensation can explain (confidence computed dynamically). */
    REACH(20.0, 1.0),
    /** Aim: superhuman angular precision (very low offset + very low ray error). */
    AIM_CONSISTENCY(12.0, 0.80),
    /** Rotation: a large turn with no human deceleration curve (still accelerating at the hit). */
    KINEMATIC_ROBOTIC(10.0, 0.70),
    /** Silent rotation: client-side rotation not matching server-side (packet-level discrepancy). */
    SILENT_ROTATION(14.0, 0.85),
    /** Unnatural jerk: third-derivative of rotation exceeds human capability. */
    UNNATURAL_JERK(8.0, 0.75),
    /** Perfect cooldown sync: attack timing locked to exact server tick with inhuman precision. */
    PERFECT_COOLDOWN_SYNC(6.0, 0.65),
    /** The ONNX model output, weighted by its (currently low) trust — a minor contributor by design. */
    AI_MODEL(0.0, RiskEngine.MODEL_TRUST);

    private final double baseWeight;
    private final double defaultConfidence;

    SignalType(double baseWeight, double defaultConfidence) {
        this.baseWeight = baseWeight;
        this.defaultConfidence = defaultConfidence;
    }

    public double baseWeight() { return baseWeight; }

    public double defaultConfidence() { return defaultConfidence; }
}
