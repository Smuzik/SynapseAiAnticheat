package net.synapselabs.anticheat.engine;

import net.synapselabs.anticheat.data.ThreatState;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable snapshot representing a complete analysis evaluation event.
 * Feeds cleanly into AlertManager, OverheadDisplayManager, PlayerProfile, and PunishmentManager.
 */
public record DetectionSnapshot(
    UUID playerId,
    String playerName,
    long timestamp,
    float rawScore,
    float confidence,
    float suspicion,
    float killauraConfidence,
    float aimConfidence,
    ThreatState threatState,
    List<String> hardViolations,
    List<String> aiViolations,
    FeatureVector featureVector,
    Map<String, Object> metadata,
    String verdict,          // Risk Engine verdict: LEGIT | SUSPICIOUS | CHEAT
    float riskScore,         // aggregated risk, normalised to 0..1 (risk/100)
    List<String> reasons     // human-readable top contributions, always populated (even SUSPICIOUS)
) {
    public boolean hasHardViolations() {
        return hardViolations != null && !hardViolations.isEmpty();
    }

    public boolean hasAiViolations() {
        return aiViolations != null && !aiViolations.isEmpty();
    }

    /** Flagged = worth showing to staff (SUSPICIOUS or CHEAT), or any legacy violation lane populated. */
    public boolean isFlagged() {
        return (verdict != null && !"LEGIT".equals(verdict)) || hasHardViolations() || hasAiViolations();
    }

    /** Actionable = the Risk Engine reached CHEAT. ONLY this ever triggers punishment. */
    public boolean isActionable() {
        return "CHEAT".equals(verdict);
    }

    public String getPrimaryViolation() {
        if (hasHardViolations()) {
            return hardViolations.get(0);
        }
        if (hasAiViolations()) {
            return aiViolations.get(0);
        }
        if (reasons != null && !reasons.isEmpty()) {
            return reasons.get(0);
        }
        return "Clean";
    }

    public int getRiskPercent() {
        return Math.round(Math.max(0.0f, Math.min(1.0f, riskScore)) * 100.0f);
    }

    public int getConfidencePercent() {
        return Math.round(confidence * 100.0f);
    }

    public int getSuspicionPercent() {
        return Math.round(suspicion * 100.0f);
    }

    public int getKillauraPercent() {
        return Math.round(killauraConfidence * 100.0f);
    }

    public int getAimPercent() {
        return Math.round(aimConfidence * 100.0f);
    }

    public static Builder builder(UUID playerId, String playerName) {
        return new Builder(playerId, playerName);
    }

    public static class Builder {
        private final UUID playerId;
        private final String playerName;
        private long timestamp = System.currentTimeMillis();
        private float rawScore = 0.0f;
        private float confidence = 0.0f;
        private float suspicion = 0.0f;
        private float killauraConfidence = 0.0f;
        private float aimConfidence = 0.0f;
        private ThreatState threatState = ThreatState.CLEAN;
        private List<String> hardViolations = Collections.emptyList();
        private List<String> aiViolations = Collections.emptyList();
        private FeatureVector featureVector = null;
        private Map<String, Object> metadata = Collections.emptyMap();
        private String verdict = "LEGIT";
        private float riskScore = 0.0f;
        private List<String> reasons = Collections.emptyList();

        public Builder(UUID playerId, String playerName) {
            this.playerId = playerId;
            this.playerName = playerName;
        }

        public Builder timestamp(long timestamp) { this.timestamp = timestamp; return this; }
        public Builder rawScore(float rawScore) { this.rawScore = rawScore; return this; }
        public Builder confidence(float confidence) { this.confidence = confidence; return this; }
        public Builder suspicion(float suspicion) { this.suspicion = suspicion; return this; }
        public Builder killauraConfidence(float killauraConfidence) { this.killauraConfidence = killauraConfidence; return this; }
        public Builder aimConfidence(float aimConfidence) { this.aimConfidence = aimConfidence; return this; }
        public Builder threatState(ThreatState threatState) { this.threatState = threatState; return this; }
        public Builder hardViolations(List<String> hardViolations) { this.hardViolations = hardViolations; return this; }
        public Builder aiViolations(List<String> aiViolations) { this.aiViolations = aiViolations; return this; }
        public Builder featureVector(FeatureVector featureVector) { this.featureVector = featureVector; return this; }
        public Builder metadata(Map<String, Object> metadata) { this.metadata = metadata; return this; }
        public Builder verdict(String verdict) { this.verdict = verdict; return this; }
        public Builder riskScore(float riskScore) { this.riskScore = riskScore; return this; }
        public Builder reasons(List<String> reasons) { this.reasons = reasons; return this; }

        public DetectionSnapshot build() {
            return new DetectionSnapshot(
                playerId,
                playerName,
                timestamp,
                rawScore,
                confidence,
                suspicion,
                killauraConfidence,
                aimConfidence,
                threatState,
                hardViolations,
                aiViolations,
                featureVector,
                metadata,
                verdict,
                riskScore,
                reasons
            );
        }
    }
}
