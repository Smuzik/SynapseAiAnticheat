package net.synapselabs.anticheat.data;

import java.text.SimpleDateFormat;
import java.util.*;

public class PlayerProfile {
    private final UUID uuid;
    private String name;
    private long firstJoin;
    private long lastJoin;
    private String lastIp;
    private final Set<String> knownIps = new HashSet<>();
    private int aiFlagsCount = 0;
    private int grimFlagsCount = 0;
    private boolean banned = false;
    private String lastFlagReason = "Нет";
    private float lastFlagConfidence = 0.0f;

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

    public String getLastIp() { return lastIp; }
    public Set<String> getKnownIps() { return knownIps; }

    public int getAiFlagsCount() { return aiFlagsCount; }
    public void incrementAiFlags(String reason, float confidence) {
        this.aiFlagsCount++;
        this.lastFlagReason = reason;
        this.lastFlagConfidence = confidence;
    }

    public int getGrimFlagsCount() { return grimFlagsCount; }
    public void incrementGrimFlags() { this.grimFlagsCount++; }

    public boolean isBanned() { return banned; }
    public void setBanned(boolean banned) { this.banned = banned; }

    public String getLastFlagReason() { return lastFlagReason; }
    public float getLastFlagConfidence() { return lastFlagConfidence; }

    public String getFormattedFirstJoin() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm");
        return sdf.format(new Date(firstJoin));
    }
}
