package net.synapselabs.anticheat.engine;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Versioned feature vector for the combat model.
 *
 * <p><b>Train/serve parity (Phase 2).</b> The model input is assembled ONLY via
 * {@link FeatureSchema#assemble(Map)} from {@link #namedFeatures()}, whose keys are the canonical
 * {@link FeatureSchema#NAMES} and whose values are <b>SIGNED</b> — never {@code Math.abs()}-ed. This is the
 * exact same schema, order and sign convention the collector wrote the training data with (see
 * {@code TelemetrySample} + {@code MathUtils} in freecam-pvp-collector). The old serving path applied
 * {@code Math.abs()} to the directional features, hard-coded {@code raycast_hit = 1.0}, and mis-mapped the
 * distance/angle slots — a textbook train/serve skew that inflated suspicion on legitimate players. That
 * path no longer exists: there is one canonical assembly and both raw and served vectors go through it.
 */
public record FeatureVector(
    String version,
    float[] rawValues,
    float[] normalizedValues,
    Map<String, Float> namedFeatures
) {
    public static final String CURRENT_VERSION = "v1";

    /**
     * Build a canonical {@code combat.v1} feature vector.
     *
     * <p>All yaw/pitch deltas and accelerations MUST be SIGNED — the sign of {@code yawAccel} (accelerating
     * vs decelerating onto the target) is the single most discriminative feature and must reach the model
     * intact. {@code raycastAngleError} follows the collector convention: {@code 0} on a clean hit,
     * otherwise the angular miss to the target box centre. {@code distance} is the eye-to-eye distance;
     * {@code raycastDistance} is the ray's hit distance (or distance to the box on a miss).
     */
    public static FeatureVector createV1(
        float distance,
        float angleOffsetDeg,
        float yawDelta1t,
        float yawDelta2t,
        float yawDelta5t,
        float yawAccel,
        float pitchDelta1t,
        float pitchDelta2t,
        float pitchDelta5t,
        float pitchAccel,
        boolean raycastHit,
        float raycastDistance,
        float raycastAngleError,
        boolean isFalling,
        boolean isSprinting,
        float attackCooldownProgress
    ) {
        // Canonical, SIGNED name -> value map, keyed by FeatureSchema.NAMES (index-aligned).
        Map<String, Float> named = new LinkedHashMap<>();
        named.put(FeatureSchema.NAMES[FeatureSchema.I_DISTANCE], distance);
        named.put(FeatureSchema.NAMES[FeatureSchema.I_ANGLE_OFFSET_DEG], angleOffsetDeg);
        named.put(FeatureSchema.NAMES[FeatureSchema.I_YAW_DELTA_1T], yawDelta1t);
        named.put(FeatureSchema.NAMES[FeatureSchema.I_YAW_DELTA_2T], yawDelta2t);
        named.put(FeatureSchema.NAMES[FeatureSchema.I_YAW_DELTA_5T], yawDelta5t);
        named.put(FeatureSchema.NAMES[FeatureSchema.I_YAW_ACCEL], yawAccel);
        named.put(FeatureSchema.NAMES[FeatureSchema.I_PITCH_DELTA_1T], pitchDelta1t);
        named.put(FeatureSchema.NAMES[FeatureSchema.I_PITCH_DELTA_2T], pitchDelta2t);
        named.put(FeatureSchema.NAMES[FeatureSchema.I_PITCH_DELTA_5T], pitchDelta5t);
        named.put(FeatureSchema.NAMES[FeatureSchema.I_PITCH_ACCEL], pitchAccel);
        named.put(FeatureSchema.NAMES[FeatureSchema.I_RAYCAST_HIT], raycastHit ? 1.0f : 0.0f);
        named.put(FeatureSchema.NAMES[FeatureSchema.I_RAYCAST_DISTANCE], raycastDistance);
        named.put(FeatureSchema.NAMES[FeatureSchema.I_RAYCAST_ANGLE_ERROR], raycastAngleError);
        named.put(FeatureSchema.NAMES[FeatureSchema.I_IS_FALLING], isFalling ? 1.0f : 0.0f);
        named.put(FeatureSchema.NAMES[FeatureSchema.I_IS_SPRINTING], isSprinting ? 1.0f : 0.0f);
        named.put(FeatureSchema.NAMES[FeatureSchema.I_ATTACK_COOLDOWN], attackCooldownProgress);

        // The exact vector the ONNX model consumes: assembled once, in canonical order, signs intact.
        float[] canonical = FeatureSchema.assemble(named);

        // rawValues == the canonical model input. normalizedValues is retained for record-shape
        // compatibility only; the model is trained on raw signed values, so no separate normalisation is
        // fed to it (kept equal to raw to avoid ever re-introducing a skew).
        return new FeatureVector(CURRENT_VERSION, canonical, canonical.clone(),
                Collections.unmodifiableMap(named));
    }

    /**
     * The 16 features for the ONNX model, in canonical {@link FeatureSchema} order with signs preserved.
     * Delegates to {@link FeatureSchema#assemble(Map)} so serving can never drift from the training schema.
     */
    public float[] getInferenceInput16() {
        return FeatureSchema.assemble(namedFeatures);
    }
}
