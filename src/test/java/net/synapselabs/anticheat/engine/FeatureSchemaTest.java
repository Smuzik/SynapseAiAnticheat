package net.synapselabs.anticheat.engine;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the canonical feature schema — the single source of truth for model I/O.
 *
 * <p>These lock in the two properties whose violation historically caused false positives:
 * (1) features are assembled in the exact trained order, and (2) signed directional features are
 * NEVER passed through {@code Math.abs()}.
 */
class FeatureSchemaTest {

    @Test
    void countAndNamesAreConsistent() {
        assertEquals(24, FeatureSchema.FEATURE_COUNT);
        assertEquals(FeatureSchema.FEATURE_COUNT, FeatureSchema.NAMES.length,
                "NAMES[] length must equal FEATURE_COUNT");
    }

    @Test
    void indexConstantsMatchNameOrder() {
        assertEquals("distance", FeatureSchema.NAMES[FeatureSchema.I_DISTANCE]);
        assertEquals("angle_offset_deg", FeatureSchema.NAMES[FeatureSchema.I_ANGLE_OFFSET_DEG]);
        assertEquals("yaw_delta_1t", FeatureSchema.NAMES[FeatureSchema.I_YAW_DELTA_1T]);
        assertEquals("yaw_accel", FeatureSchema.NAMES[FeatureSchema.I_YAW_ACCEL]);
        assertEquals("yaw_jerk", FeatureSchema.NAMES[FeatureSchema.I_YAW_JERK]);
        assertEquals("pitch_accel", FeatureSchema.NAMES[FeatureSchema.I_PITCH_ACCEL]);
        assertEquals("pitch_jerk", FeatureSchema.NAMES[FeatureSchema.I_PITCH_JERK]);
        assertEquals("raycast_hit", FeatureSchema.NAMES[FeatureSchema.I_RAYCAST_HIT]);
        assertEquals("raycast_distance", FeatureSchema.NAMES[FeatureSchema.I_RAYCAST_DISTANCE]);
        assertEquals("raycast_angle_error", FeatureSchema.NAMES[FeatureSchema.I_RAYCAST_ANGLE_ERROR]);
        assertEquals("hitbox_edge_proximity", FeatureSchema.NAMES[FeatureSchema.I_HITBOX_EDGE_PROXIMITY]);
        assertEquals("is_falling", FeatureSchema.NAMES[FeatureSchema.I_IS_FALLING]);
        assertEquals("is_sprinting", FeatureSchema.NAMES[FeatureSchema.I_IS_SPRINTING]);
        assertEquals("attack_cooldown_progress", FeatureSchema.NAMES[FeatureSchema.I_ATTACK_COOLDOWN]);
        assertEquals("rotation_variance_10t", FeatureSchema.NAMES[FeatureSchema.I_ROTATION_VARIANCE_10T]);
        assertEquals("attack_interval_ticks", FeatureSchema.NAMES[FeatureSchema.I_ATTACK_INTERVAL_TICKS]);
        assertEquals("target_switch_ticks", FeatureSchema.NAMES[FeatureSchema.I_TARGET_SWITCH_TICKS]);
        assertEquals("attacker_velocity_sq", FeatureSchema.NAMES[FeatureSchema.I_ATTACKER_VELOCITY_SQ]);
        assertEquals("victim_velocity_sq", FeatureSchema.NAMES[FeatureSchema.I_VICTIM_VELOCITY_SQ]);
    }

    @Test
    void assemblePlacesEachFeatureAtItsCanonicalIndex() {
        Map<String, Float> in = new HashMap<>();
        for (int i = 0; i < FeatureSchema.FEATURE_COUNT; i++) {
            in.put(FeatureSchema.NAMES[i], (float) i);   // value == index
        }
        float[] out = FeatureSchema.assemble(in);
        for (int i = 0; i < FeatureSchema.FEATURE_COUNT; i++) {
            assertEquals((float) i, out[i], 0.0f,
                    "feature '" + FeatureSchema.NAMES[i] + "' landed at the wrong slot");
        }
    }

    @Test
    void assemblePreservesSignOfDirectionalFeatures() {
        Map<String, Float> in = new HashMap<>();
        in.put("yaw_delta_1t", -152.0f);
        in.put("yaw_accel", -84.0f);
        in.put("pitch_accel", -6.0f);
        float[] out = FeatureSchema.assemble(in);
        assertEquals(-152.0f, out[FeatureSchema.I_YAW_DELTA_1T], 0.0f,
                "sign of yaw_delta must be preserved (no Math.abs)");
        assertEquals(-84.0f, out[FeatureSchema.I_YAW_ACCEL], 0.0f,
                "sign of yaw_accel is the key legit-vs-cheat signal — must be preserved");
        assertEquals(-6.0f, out[FeatureSchema.I_PITCH_ACCEL], 0.0f);
    }

    @Test
    void missingFeaturesFallBackToNeutralDefaults() {
        float[] out = FeatureSchema.assemble(new HashMap<>());   // nothing provided
        assertEquals(3.0f, out[FeatureSchema.I_DISTANCE], 0.0f);
        assertEquals(10.0f, out[FeatureSchema.I_ANGLE_OFFSET_DEG], 0.0f);
        assertEquals(1.0f, out[FeatureSchema.I_RAYCAST_HIT], 0.0f);
        assertEquals(3.0f, out[FeatureSchema.I_RAYCAST_DISTANCE], 0.0f);
        assertEquals(10.0f, out[FeatureSchema.I_RAYCAST_ANGLE_ERROR], 0.0f);
        assertEquals(1.0f, out[FeatureSchema.I_ATTACK_COOLDOWN], 0.0f);
        assertEquals(0.0f, out[FeatureSchema.I_YAW_DELTA_1T], 0.0f);
        assertNull(FeatureSchema.validate(out), "neutral vector must be valid");
    }

    @Test
    void nanAndInfinityFallBackToNeutralDefaults() {
        Map<String, Float> in = new HashMap<>();
        in.put("distance", Float.NaN);
        in.put("angle_offset_deg", Float.POSITIVE_INFINITY);
        float[] out = FeatureSchema.assemble(in);
        assertEquals(3.0f, out[FeatureSchema.I_DISTANCE], 0.0f);
        assertEquals(10.0f, out[FeatureSchema.I_ANGLE_OFFSET_DEG], 0.0f);
        assertNull(FeatureSchema.validate(out));
    }

    @Test
    void validateRejectsWrongWidthAndNonFinite() {
        assertNotNull(FeatureSchema.validate(null));
        assertNotNull(FeatureSchema.validate(new float[23]));
        float[] bad = new float[24];
        bad[5] = Float.NaN;
        assertNotNull(FeatureSchema.validate(bad));
        assertNull(FeatureSchema.validate(new float[24]));
    }

    @Test
    void versionTagsAreWired() {
        assertEquals("combat.v2", FeatureSchema.VERSION);
        assertEquals(FeatureSchema.VERSION, SchemaVersions.FEATURE_SCHEMA_VERSION);
        assertNotNull(SchemaVersions.MODEL_VERSION_DEFAULT);
        assertNotNull(SchemaVersions.DATASET_VERSION);
    }
}
