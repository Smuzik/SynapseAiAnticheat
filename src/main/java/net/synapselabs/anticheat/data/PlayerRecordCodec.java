package net.synapselabs.anticheat.data;

import java.util.Locale;
import java.util.UUID;

/**
 * Dependency-free (de)serialization of a {@link PlayerProfile} to a single {@code ;}-delimited line
 * for {@code player_database.txt}.
 *
 * <p><b>Why this exists.</b> The original loader parsed {@code aiFlags}/{@code grimFlags} into unused
 * locals and then called {@code incrementAiFlags(...)}, which collapsed aiFlags to exactly 1 and
 * discarded the saved counters, threat state and confidences on every restart. This codec restores
 * every field faithfully and extends the line with the previously-dropped escalation state.</p>
 *
 * <p><b>Backward compatibility.</b> The format is versioned by <em>field count</em> (append-only), so
 * older 8- and 10-field lines still load:</p>
 * <ul>
 *   <li>fields 0-7  (v0): uuid, name, firstJoin, lastJoin, lastIp, aiFlags, grimFlagsCount, banned</li>
 *   <li>fields 8-9  (v1): lastFlagReason, lastFlagConfidence</li>
 *   <li>fields 10-16 (v2): hardFlags, totalFlags, threatState, killauraConfidence, aimConfidence,
 *       suspicion, lastFlagTimestamp</li>
 * </ul>
 *
 * <p><b>Robustness.</b> Floats are always written with {@link Locale#ROOT} so a comma-decimal JVM
 * locale (e.g. ru-RU) cannot corrupt the numeric fields (a comma would both split wrong on {@code ;}
 * and fail {@link Float#parseFloat}). The two free-text fields (name, reason) are sanitized so a stray
 * {@code ;} / newline cannot shift the column count.</p>
 *
 * <p>This class depends only on {@link PlayerProfile} and {@link ThreatState} (no Bukkit), so the
 * round-trip is unit-testable under the plugin's {@code compileOnly} purpur-api setup.</p>
 */
public final class PlayerRecordCodec {

    /** Number of fields in the current (v2) line format. */
    static final int FIELDS_V2 = 17;

    private PlayerRecordCodec() {}

    /** Serialize a profile to one line (no trailing newline). */
    public static String encode(PlayerProfile p) {
        return String.format(
                Locale.ROOT,
                "%s;%s;%d;%d;%s;%d;%d;%b;%s;%.4f;%d;%d;%s;%.4f;%.4f;%.4f;%d",
                p.getUuid(),
                sanitize(p.getName()),
                p.getFirstJoin(),
                p.getLastJoin(),
                p.getLastIp(),
                p.getAiFlags(),
                p.getGrimFlagsCount(),
                p.isBanned(),
                sanitize(p.getLastFlagReason()),
                p.getLastFlagConfidence(),
                p.getHardFlags(),
                p.getTotalFlags(),
                p.getThreatState().name(),
                p.getKillauraConfidence(),
                p.getAimConfidence(),
                p.getSuspicion(),
                p.getLastFlagTimestamp()
        );
    }

    /**
     * Parse one line into a profile, or {@code null} if the line is blank or its required core fields
     * (fields 0-7) are malformed. Optional extended fields fall back to safe defaults individually so a
     * single corrupt confidence never drops a player's ban/flag state.
     */
    public static PlayerProfile decode(String line) {
        if (line == null) return null;
        String trimmed = line.trim();
        if (trimmed.isEmpty()) return null;

        // -1 keeps trailing empty fields so the column count stays stable.
        String[] parts = trimmed.split(";", -1);
        if (parts.length < 8) return null;

        try {
            UUID uuid = UUID.fromString(parts[0].trim());
            String name = parts[1];
            long firstJoin = Long.parseLong(parts[2].trim());
            long lastJoin = Long.parseLong(parts[3].trim());
            String ip = parts[4];
            int aiFlags = Integer.parseInt(parts[5].trim());
            int grimFlags = Integer.parseInt(parts[6].trim());
            boolean banned = Boolean.parseBoolean(parts[7].trim());

            PlayerProfile p = new PlayerProfile(uuid, name, ip);
            p.setFirstJoin(firstJoin);
            p.setLastJoin(lastJoin);
            p.setBanned(banned);
            p.setAiFlags(aiFlags);            // fixes the historical collapse-to-1 bug
            p.setGrimFlagsCount(grimFlags);

            // v1: last-flag reason + confidence
            if (parts.length >= 10) {
                p.setLastFlagReason(parts[8]);
                p.setLastFlagConfidence(parseFloat(parts[9], 0.0f));
            }

            // v2: full escalation state
            if (parts.length >= FIELDS_V2) {
                int hardFlags = parseInt(parts[10], 0);
                p.setHardFlags(hardFlags);
                p.setTotalFlags(parseInt(parts[11], hardFlags + aiFlags + grimFlags));
                p.setThreatState(parseThreat(parts[12]));
                p.setKillauraConfidence(parseFloat(parts[13], 0.0f));
                p.setAimConfidence(parseFloat(parts[14], 0.0f));
                p.setSuspicion(parseFloat(parts[15], 0.0f));
                p.setLastFlagTimestamp(parseLong(parts[16], 0L));
            } else {
                // Legacy lines never stored hard/total; reconstruct total so it isn't understated.
                p.setTotalFlags(aiFlags + grimFlags);
            }
            return p;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Replace field separators / newlines in free-text so they cannot shift the column count. */
    static String sanitize(String s) {
        if (s == null) return "";
        return s.replace(';', ',').replace('\n', ' ').replace('\r', ' ');
    }

    private static ThreatState parseThreat(String s) {
        try {
            return ThreatState.valueOf(s.trim());
        } catch (RuntimeException e) {
            return ThreatState.CLEAN;
        }
    }

    private static int parseInt(String s, int fallback) {
        try { return Integer.parseInt(s.trim()); } catch (RuntimeException e) { return fallback; }
    }

    private static long parseLong(String s, long fallback) {
        try { return Long.parseLong(s.trim()); } catch (RuntimeException e) { return fallback; }
    }

    private static float parseFloat(String s, float fallback) {
        try { return Float.parseFloat(s.trim()); } catch (RuntimeException e) { return fallback; }
    }
}
