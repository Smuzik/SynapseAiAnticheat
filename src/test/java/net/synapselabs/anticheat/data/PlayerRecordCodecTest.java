package net.synapselabs.anticheat.data;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Round-trip guard for {@link PlayerRecordCodec}. Proves the persistence bug is fixed: aiFlags no longer
 * collapses to 1, and hardFlags/totalFlags/threatState/confidences/timestamp survive a save->load cycle.
 * Also proves legacy (8- and 10-field) lines still load and that the format is locale-safe.
 */
class PlayerRecordCodecTest {

    private static final float EPS = 1e-3f;

    private static PlayerProfile sampleProfile() {
        PlayerProfile p = new PlayerProfile(
                UUID.fromString("11111111-2222-3333-4444-555555555555"), "Notch", "203.0.113.7");
        p.setFirstJoin(1_600_000_000_000L);
        p.setLastJoin(1_600_000_500_000L);
        p.setAiFlags(7);
        p.setHardFlags(3);
        p.setGrimFlagsCount(2);
        p.setTotalFlags(12);
        p.setBanned(true);
        p.setThreatState(ThreatState.HIGH_CONFIDENCE);
        p.setKillauraConfidence(0.8125f);
        p.setAimConfidence(0.6250f);
        p.setSuspicion(0.4375f);
        p.setLastFlagReason("AIM_CONSISTENCY");
        p.setLastFlagConfidence(0.9000f);
        p.setLastFlagTimestamp(1_600_000_499_000L);
        return p;
    }

    private static void assertSameState(PlayerProfile e, PlayerProfile a) {
        assertEquals(e.getUuid(), a.getUuid(), "uuid");
        assertEquals(e.getName(), a.getName(), "name");
        assertEquals(e.getFirstJoin(), a.getFirstJoin(), "firstJoin");
        assertEquals(e.getLastJoin(), a.getLastJoin(), "lastJoin");
        assertEquals(e.getLastIp(), a.getLastIp(), "lastIp");
        assertEquals(e.getAiFlags(), a.getAiFlags(), "aiFlags");
        assertEquals(e.getHardFlags(), a.getHardFlags(), "hardFlags");
        assertEquals(e.getGrimFlagsCount(), a.getGrimFlagsCount(), "grimFlagsCount");
        assertEquals(e.getTotalFlags(), a.getTotalFlags(), "totalFlags");
        assertEquals(e.isBanned(), a.isBanned(), "banned");
        assertEquals(e.getThreatState(), a.getThreatState(), "threatState");
        assertEquals(e.getKillauraConfidence(), a.getKillauraConfidence(), EPS, "killauraConfidence");
        assertEquals(e.getAimConfidence(), a.getAimConfidence(), EPS, "aimConfidence");
        assertEquals(e.getSuspicion(), a.getSuspicion(), EPS, "suspicion");
        assertEquals(e.getLastFlagReason(), a.getLastFlagReason(), "lastFlagReason");
        assertEquals(e.getLastFlagConfidence(), a.getLastFlagConfidence(), EPS, "lastFlagConfidence");
        assertEquals(e.getLastFlagTimestamp(), a.getLastFlagTimestamp(), "lastFlagTimestamp");
    }

    @Test
    void fullRoundTripPreservesEveryField() {
        PlayerProfile original = sampleProfile();
        PlayerProfile restored = PlayerRecordCodec.decode(PlayerRecordCodec.encode(original));
        assertNotNull(restored, "round-trip must not drop the profile");
        assertSameState(original, restored);
    }

    @Test
    void aiFlagsDoNotCollapseToOne() {
        // The old loader called incrementAiFlags(...) which forced aiFlags == 1 regardless of the saved
        // count. This is the primary regression to guard.
        PlayerProfile restored = PlayerRecordCodec.decode(PlayerRecordCodec.encode(sampleProfile()));
        assertNotNull(restored);
        assertEquals(7, restored.getAiFlags(), "aiFlags must survive, not collapse to 1");
        assertEquals(3, restored.getHardFlags(), "hardFlags must survive (was never persisted before)");
        assertEquals(ThreatState.HIGH_CONFIDENCE, restored.getThreatState(),
                "threatState must survive (was never persisted before)");
    }

    @Test
    void encodedLineHasStableColumnCount() {
        String line = PlayerRecordCodec.encode(sampleProfile());
        assertEquals(PlayerRecordCodec.FIELDS_V2, line.split(";", -1).length,
                "v2 line must have exactly 17 fields");
    }

    @Test
    void floatsUseDotDecimalRegardlessOfLocale() {
        // Locale.ROOT in encode() guarantees '.' — a comma would both mis-split on ';' and fail
        // Float.parseFloat on a comma-decimal JVM (e.g. ru-RU).
        String line = PlayerRecordCodec.encode(sampleProfile());
        assertTrue(line.contains("0.8125"), "killaura confidence must render with a dot: " + line);
        assertFalse(line.contains(","), "encoded line must not contain a comma decimal separator: " + line);
    }

    @Test
    void reasonWithSeparatorIsSanitized() {
        PlayerProfile p = sampleProfile();
        p.setLastFlagReason("weird;reason\nwith\rbreaks");
        String line = PlayerRecordCodec.encode(p);
        assertEquals(PlayerRecordCodec.FIELDS_V2, line.split(";", -1).length,
                "a reason containing ';' must not add columns");
        PlayerProfile restored = PlayerRecordCodec.decode(line);
        assertNotNull(restored);
        // Fields AFTER the messy reason must still align.
        assertEquals(p.getLastFlagTimestamp(), restored.getLastFlagTimestamp(), "timestamp after messy reason");
        assertEquals(p.getThreatState(), restored.getThreatState(), "threatState after messy reason");
    }

    @Test
    void legacyTenFieldLineStillLoads() {
        // Old v1 format: aiFlags=5, grim=4, reason+confidence present, no extended state.
        String legacy = "11111111-2222-3333-4444-555555555555;Steve;1600000000000;1600000500000;"
                + "10.0.0.1;5;4;false;KILLAURA;0.75";
        PlayerProfile p = PlayerRecordCodec.decode(legacy);
        assertNotNull(p);
        assertEquals(5, p.getAiFlags(), "legacy aiFlags restored (not collapsed to 1)");
        assertEquals(4, p.getGrimFlagsCount(), "legacy grimFlags");
        assertEquals(9, p.getTotalFlags(), "legacy totalFlags reconstructed = ai + grim");
        assertEquals(0, p.getHardFlags(), "legacy hardFlags default 0");
        assertEquals(ThreatState.CLEAN, p.getThreatState(), "legacy threatState default CLEAN");
        assertEquals("KILLAURA", p.getLastFlagReason(), "legacy reason");
        assertEquals(0.75f, p.getLastFlagConfidence(), EPS, "legacy confidence");
    }

    @Test
    void legacyEightFieldLineStillLoads() {
        String legacy = "11111111-2222-3333-4444-555555555555;Alex;1600000000000;1600000500000;10.0.0.2;3;1;true";
        PlayerProfile p = PlayerRecordCodec.decode(legacy);
        assertNotNull(p);
        assertEquals(3, p.getAiFlags());
        assertEquals(1, p.getGrimFlagsCount());
        assertTrue(p.isBanned());
        assertEquals(4, p.getTotalFlags(), "total reconstructed = ai + grim");
        assertEquals("None", p.getLastFlagReason(), "no reason field -> constructor default");
    }

    @Test
    void malformedLinesReturnNull() {
        assertNull(PlayerRecordCodec.decode(null));
        assertNull(PlayerRecordCodec.decode(""));
        assertNull(PlayerRecordCodec.decode("   "));
        assertNull(PlayerRecordCodec.decode("too;few;fields"));
        assertNull(PlayerRecordCodec.decode("not-a-uuid;Name;1;2;ip;0;0;false"));
    }
}
