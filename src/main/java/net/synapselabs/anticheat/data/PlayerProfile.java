package net.synapselabs.anticheat.data;

import net.synapselabs.anticheat.engine.DetectionSnapshot;

import java.text.SimpleDateFormat;
import java.util.*;

public class PlayerProfile {
    private final UUID uuid;
    private String name;
    private long firstJoin;
    private long lastJoin;
    private String lastIp;
    private final Set<String> knownIps = new HashSet<>();

    private int totalFlags = 0;
    private int hardFlags = 0;
    private int aiFlags = 0;
    private int grimFlagsCount = 0;

    private float killauraConfidence = 0.0f;
    private float aimConfidence = 0.0f;
    private float suspicion = 0.0f;
    private ThreatState threatState = ThreatState.CLEAN;

    private boolean banned = false;
    private String lastFlagReason = "None";
    private float lastFlagConfidence = 0.0f;
    private long lastFlagTimestamp = 0;

    public PlayerProfile(UUID uuid, String name, String ip) {
        this.uuid = uuid;
        this.name = name;
        this.firstJoin = System.currentTimeMillis();
        this.lastJoin = System.currentTimeMillis();
        this.lastIp = ip;
        if (ip != null) {
            this.knownIps.add(ip);
        }
    }

    public UUID getUuid() { return uuid; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public long getFirstJoin() { return firstJoin; }
    public void setFirstJoin(long firstJoin) { this.firstJoin = firstJoin; }

    public long getLastJoin() { return lastJoin; }
    public void updateLastJoin(String ip) {
        this.lastJoin = System.currentTimeMillis();
        this.lastIp = ip;
        if (ip != null) this.knownIps.add(ip);
    }
    /** Restore the persisted last-join time verbatim (used when loading the database, not on a live join). */
    public void setLastJoin(long lastJoin) { this.lastJoin = lastJoin; }

    public String getLastIp() { return lastIp; }
    public Set<String> getKnownIps() { return knownIps; }

    public int getTotalFlags() { return totalFlags; }
    public int getHardFlags() { return hardFlags; }
    public int getAiFlags() { return aiFlags; }

    // Direct setters used only to restore persisted escalation state on load. Unlike the
    // increment* methods they do NOT touch lastFlagReason / lastFlagConfidence / lastFlagTimestamp.
    public void setTotalFlags(int totalFlags) { this.totalFlags = totalFlags; }
    public void setHardFlags(int hardFlags) { this.hardFlags = hardFlags; }
    public void setAiFlags(int aiFlags) { this.aiFlags = aiFlags; }

    public void incrementHardFlags(String reason) {
        this.totalFlags++;
        this.hardFlags++;
        this.lastFlagReason = reason;
        this.lastFlagConfidence = 1.0f;
        this.lastFlagTimestamp = System.currentTimeMillis();
    }

    public void incrementAiFlags(String reason, float confidence) {
        this.totalFlags++;
        this.aiFlags++;
        this.lastFlagReason = reason;
        this.lastFlagConfidence = confidence;
        this.lastFlagTimestamp = System.currentTimeMillis();
    }

    public void resetFlags() {
        this.totalFlags = 0;
        this.hardFlags = 0;
        this.aiFlags = 0;
        this.killauraConfidence = 0.0f;
        this.aimConfidence = 0.0f;
        this.suspicion = 0.0f;
        this.threatState = ThreatState.CLEAN;
    }

    public void applySnapshot(DetectionSnapshot snapshot) {
        this.killauraConfidence = snapshot.killauraConfidence();
        this.aimConfidence = snapshot.aimConfidence();
        this.suspicion = snapshot.suspicion();
        this.threatState = snapshot.threatState();

        if (snapshot.hasHardViolations()) {
            incrementHardFlags(snapshot.getPrimaryViolation());
        } else if (snapshot.hasAiViolations()) {
            incrementAiFlags(snapshot.getPrimaryViolation(), snapshot.confidence());
        }
    }

    public float getKillauraConfidence() { return killauraConfidence; }
    public void setKillauraConfidence(float killauraConfidence) { this.killauraConfidence = killauraConfidence; }

    public float getAimConfidence() { return aimConfidence; }
    public void setAimConfidence(float aimConfidence) { this.aimConfidence = aimConfidence; }

    public float getSuspicion() { return suspicion; }
    public void setSuspicion(float suspicion) { this.suspicion = suspicion; }

    public ThreatState getThreatState() { return threatState; }
    public void setThreatState(ThreatState threatState) { this.threatState = threatState; }

    public int getGrimFlagsCount() { return grimFlagsCount; }
    public void setGrimFlagsCount(int grimFlagsCount) { this.grimFlagsCount = grimFlagsCount; }
    public void incrementGrimFlags() {
        this.grimFlagsCount++; 
        this.totalFlags++;
    }
    public void incrementGrimFlags(String check, double vl) {
        this.grimFlagsCount++;
        this.totalFlags++;
        this.lastFlagReason = "GrimAC: " + check + " (x" + (int)vl + ")";
        this.lastFlagTimestamp = System.currentTimeMillis();
    }

    public boolean isBanned() { return banned; }
    public void setBanned(boolean banned) { this.banned = banned; }

    public String getLastFlagReason() { return lastFlagReason; }
    public float getLastFlagConfidence() { return lastFlagConfidence; }
    public long getLastFlagTimestamp() { return lastFlagTimestamp; }

    public void setLastFlagReason(String lastFlagReason) { this.lastFlagReason = lastFlagReason; }
    public void setLastFlagConfidence(float lastFlagConfidence) { this.lastFlagConfidence = lastFlagConfidence; }
    public void setLastFlagTimestamp(long lastFlagTimestamp) { this.lastFlagTimestamp = lastFlagTimestamp; }

    public String getFormattedFirstJoin() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm");
        return sdf.format(new Date(firstJoin));
    }
}
