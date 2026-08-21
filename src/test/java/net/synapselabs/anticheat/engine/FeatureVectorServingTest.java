package net.synapselabs.anticheat.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Serving-path parity tests (Phase 2). These lock in that {@link FeatureVector#getInferenceInput16()}
 * feeds the model the SAME thing the collector trained it on: canonical order, signed directional
 * features, an honest {@code raycast_hit}, and correct distance/angle slot mapping.
 *
 * <p>Each assertion here corresponds to a specific bug in the old serving path:
 * <ul>
 *   <li>{@code Math.abs()} on yaw/pitch deltas + accel (destroyed the decel signal),</li>
 *   <li>a hard-coded {@code raycast_hit = 1.0} (the model never saw a miss),</li>
 *   <li>{@code raycast_distance} slot fed with {@code distanceMean}, and {@code distance} slot fed with
 *       the raycast distance (mis-mapped columns).</li>
 * </ul>
 */
class FeatureVectorServingTest {

    /**
     * The headline false-positive scenario (mirror of {@code corner_360_crit_legit.json}): a legit ~360°
     * flick that DECELERATES onto the target (yaw_accel negative), where the ray misses at the exact tick.
     * The whole point of Phase 2 is that these signed values and the miss reach the model unaltered.
     */
    @Test
    void servingVectorIsCanonicalSignedAndHonest() {
        FeatureVector fv = FeatureVector.createV1(
            3.05f,    // distance (eye-to-eye)
            3.5f,     // angle_offset_deg (to victim eye)
            152.0f,   // yaw_delta_1t
            268.0f,   // yaw_delta_2t
            361.0f,   // yaw_delta_5t   (a real 360° flick — must NOT be wrapped or clamped)
            -84.0f,   // yaw_accel      (DECELERATING — the decisive legit signal)
            9.0f,     // pitch_delta_1t
            14.0f,    // pitch_delta_2t
            21.0f,    // pitch_delta_5t
            -6.0f,    // pitch_accel    (signed)
            false,    // raycast_hit    (ray missed this tick)
            3.1f,     // raycast_distance
            12.0f,    // raycast_angle_error (angular miss to box centre)
            true,     // is_falling     (crit)
            false,    // is_sprinting
            1.0f      // attack_cooldown_progress (charged)
        );

        float[] v = fv.getInferenceInput16();

        assertEquals(FeatureSchema.FEATURE_COUNT, v.length, "model input must match FEATURE_COUNT");
        assertNull(FeatureSchema.validate(v), "assembled vector must be schema-valid");

        // Correct slot mapping (the old code swapped distance <-> raycast_distance and duplicated angles).
        assertEquals(3.05f, v[FeatureSchema.I_DISTANCE], 1e-6f);
        assertEquals(3.5f, v[FeatureSchema.I_ANGLE_OFFSET_DEG], 1e-6f);
        assertEquals(3.1f, v[FeatureSchema.I_RAYCAST_DISTANCE], 1e-6f);
        assertEquals(12.0f, v[FeatureSchema.I_RAYCAST_ANGLE_ERROR], 1e-6f);

        // Signs preserved — no Math.abs anywhere on the serving path.
        assertEquals(361.0f, v[FeatureSchema.I_YAW_DELTA_5T], 1e-6f, "large flick must not be wrapped/clamped");
        assertEquals(-84.0f, v[FeatureSchema.I_YAW_ACCEL], 1e-6f, "decel sign is the key legit signal");
        assertEquals(-6.0f, v[FeatureSchema.I_PITCH_ACCEL], 1e-6f);

        // Honest raycast_hit — a miss must reach the model as 0.0, never a hard-coded 1.0.
        assertEquals(0.0f, v[FeatureSchema.I_RAYCAST_HIT], 0.0f);

        // Boolean flags encoded as 0/1 at their canonical slots.
        assertEquals(1.0f, v[FeatureSchema.I_IS_FALLING], 0.0f);
        assertEquals(0.0f, v[FeatureSchema.I_IS_SPRINTING], 0.0f);
        assertEquals(1.0f, v[FeatureSchema.I_ATTACK_COOLDOWN], 0.0f);
    }

    /** A clean hit encodes raycast_hit = 1.0; the serving path never fabricates or drops the flag. */
    @Test
    void cleanHitEncodesRaycastHitTrue() {
        FeatureVector fv = FeatureVector.createV1(
            2.7f, 4.0f,
            18.0f, 31.0f, 55.0f, -9.0f,
            5.0f, 8.0f, 12.0f, -3.0f,
            true,   // raycast_hit
            2.7f,
            0.0f,   // collector convention: angle_error is 0 on a hit
            false, true, 1.0f
        );
        float[] v = fv.getInferenceInput16();
        assertEquals(1.0f, v[FeatureSchema.I_RAYCAST_HIT], 0.0f);
        assertEquals(0.0f, v[FeatureSchema.I_RAYCAST_ANGLE_ERROR], 0.0f);
        assertEquals(-9.0f, v[FeatureSchema.I_YAW_ACCEL], 1e-6f);
    }

    /** getInferenceInput16() must equal the stored rawValues (both are the one canonical assembly). */
    @Test
    void servingVectorMatchesRawValues() {
        FeatureVector fv = FeatureVector.createV1(
            3.4f, 0.4f,
            148.0f, 150.0f, 151.0f, 96.0f,
            40.0f, 41.0f, 41.0f, 33.0f,
            true, 3.4f, 0.0f,
            false, false, 1.0f
        );
        assertArrayEquals(fv.rawValues(), fv.getInferenceInput16(), 0.0f);
        assertEquals("v1", fv.version());
    }

    /** namedFeatures uses canonical keys (AlertManager + assemble both depend on this). */
    @Test
    void namedFeaturesUseCanonicalKeys() {
        FeatureVector fv = FeatureVector.createV1(
            3.0f, 5.0f,
            10.0f, 20.0f, 30.0f, -5.0f,
            2.0f, 4.0f, 6.0f, -1.0f,
            true, 3.0f, 0.0f,
            false, false, 1.0f
        );
        assertTrue(fv.namedFeatures().containsKey("raycast_distance"));
        assertTrue(fv.namedFeatures().containsKey("angle_offset_deg"));
        assertEquals(3.0f, fv.namedFeatures().get("raycast_distance"), 1e-6f);
        // legacy camelCase keys must be gone so a stale reader fails loudly instead of reading a default
        assertFalse(fv.namedFeatures().containsKey("raycastDistance"));
        assertFalse(fv.namedFeatures().containsKey("targetAngleDeg"));
    }
}
