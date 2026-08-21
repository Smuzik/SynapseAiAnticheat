package net.synapselabs.anticheat.engine;

import java.util.Map;

/**
 * Canonical, versioned feature schema (combat.v2) — the SINGLE SOURCE OF TRUTH shared between:
 *   - the Python training/extraction pipeline (mirror: python/feature_schema.py),
 *   - the collector dataset writer, and
 *   - this plugin's runtime inference path.
 *
 * <p>The ONNX model ({@code anticheat_model.onnx}) expects EXACTLY these 24 features,
 * in EXACTLY this order, with SIGNED yaw/pitch values.
 *
 * <h2>Sign convention</h2>
 * <ul>
 *   <li>yaw/pitch deltas: signed. Negative = turning left / looking up; positive = right / down.</li>
 *   <li>yaw/pitch accel: signed. Positive = accelerating rotation, negative = decelerating
 *       (a human "flick" decelerates onto the target; a killaura snap does not).</li>
 *   <li>yaw/pitch jerk: signed (3rd derivative of angle).</li>
 *   <li>hitbox_edge_proximity: normalized in [0, 1] (0 = center, 1 = perimeter).</li>
 *   <li>rotation_variance_10t: rolling standard deviation of angular velocity.</li>
 * </ul>
 */
public final class FeatureSchema {

    /** Schema identity. */
    public static final String VERSION = "combat.v2";

    /** Number of inputs the ONNX model expects: {@code float_input [None, 24]}. */
    public static final int FEATURE_COUNT = 24;

    // --- Canonical indices (match the trained model's input order exactly) ---
    public static final int I_DISTANCE               = 0;
    public static final int I_ANGLE_OFFSET_DEG       = 1;
    public static final int I_YAW_DELTA_1T           = 2;
    public static final int I_YAW_DELTA_2T           = 3;
    public static final int I_YAW_DELTA_5T           = 4;
    public static final int I_YAW_ACCEL              = 5;
    public static final int I_YAW_JERK               = 6;
    public static final int I_PITCH_DELTA_1T         = 7;
    public static final int I_PITCH_DELTA_2T         = 8;
    public static final int I_PITCH_DELTA_5T         = 9;
    public static final int I_PITCH_ACCEL            = 10;
    public static final int I_PITCH_JERK             = 11;
    public static final int I_RAYCAST_HIT            = 12;
    public static final int I_RAYCAST_DISTANCE       = 13;
    public static final int I_RAYCAST_ANGLE_ERROR    = 14;
    public static final int I_HITBOX_EDGE_PROXIMITY  = 15;
    public static final int I_IS_FALLING             = 16;
    public static final int I_IS_SPRINTING           = 17;
    public static final int I_ATTACK_COOLDOWN        = 18;
    public static final int I_ROTATION_VARIANCE_10T  = 19;
    public static final int I_ATTACK_INTERVAL_TICKS  = 20;
    public static final int I_TARGET_SWITCH_TICKS    = 21;
    public static final int I_ATTACKER_VELOCITY_SQ   = 22;
    public static final int I_VICTIM_VELOCITY_SQ     = 23;

    /** Canonical ML feature names, index-aligned with the constants above. */
    public static final String[] NAMES = {
        "distance",
        "angle_offset_deg",
        "yaw_delta_1t",
        "yaw_delta_2t",
        "yaw_delta_5t",
        "yaw_accel",
        "yaw_jerk",
        "pitch_delta_1t",
        "pitch_delta_2t",
        "pitch_delta_5t",
        "pitch_accel",
        "pitch_jerk",
        "raycast_hit",
        "raycast_distance",
        "raycast_angle_error",
        "hitbox_edge_proximity",
        "is_falling",
        "is_sprinting",
        "attack_cooldown_progress",
        "rotation_variance_10t",
        "attack_interval_ticks",
        "target_switch_ticks",
        "attacker_velocity_sq",
        "victim_velocity_sq"
    };

    private FeatureSchema() {}

    /**
     * Assembles the model input vector in canonical order from a map of canonical feature names
     * to (already correctly-signed) values. Missing keys fall back to {@code neutralDefault(index)}.
     */
    public static float[] assemble(Map<String, Float> canonicalValues) {
        float[] out = new float[FEATURE_COUNT];
        for (int i = 0; i < FEATURE_COUNT; i++) {
            Float v = canonicalValues.get(NAMES[i]);
            out[i] = (v != null && !v.isNaN() && !v.isInfinite()) ? v : neutralDefault(i);
        }
        return out;
    }

    /**
     * Neutral, "clearly legitimate" default for a feature when a live value is unavailable.
     */
    public static float neutralDefault(int index) {
        return switch (index) {
            case I_DISTANCE, I_RAYCAST_DISTANCE -> 3.0f;
            case I_ANGLE_OFFSET_DEG, I_RAYCAST_ANGLE_ERROR -> 10.0f;
            case I_RAYCAST_HIT, I_ATTACK_COOLDOWN -> 1.0f;
            case I_ROTATION_VARIANCE_10T -> 5.0f;
            case I_ATTACK_INTERVAL_TICKS -> 10.0f;
            case I_TARGET_SWITCH_TICKS -> 100.0f;
            case I_HITBOX_EDGE_PROXIMITY -> 0.0f;
            default -> 0.0f;
        };
    }

    /** Validates an assembled vector; returns null if OK, otherwise a human-readable reason. */
    public static String validate(float[] v) {
        if (v == null) return "vector is null";
        if (v.length != FEATURE_COUNT) return "expected " + FEATURE_COUNT + " features, got " + v.length;
        for (int i = 0; i < v.length; i++) {
            if (Float.isNaN(v[i]) || Float.isInfinite(v[i])) return "non-finite value at " + NAMES[i];
        }
        return null;
    }
}
