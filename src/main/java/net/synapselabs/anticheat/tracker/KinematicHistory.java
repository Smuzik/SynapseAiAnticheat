package net.synapselabs.anticheat.tracker;

import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.LinkedList;

public class KinematicHistory {
    public record Snapshot(float yaw, float pitch, double x, double y, double z, long time) {}

    private final LinkedList<Snapshot> history = new LinkedList<>();
    private static final int MAX_HISTORY = 12;

    public void push(Location loc) {
        history.addFirst(new Snapshot(
            loc.getYaw(),
            loc.getPitch(),
            loc.getX(),
            loc.getY(),
            loc.getZ(),
            System.currentTimeMillis()
        ));
        while (history.size() > MAX_HISTORY) {
            history.removeLast();
        }
    }

    public static float wrapDegrees(float degrees) {
        float f = degrees % 360.0F;
        if (f >= 180.0F) f -= 360.0F;
        if (f < -180.0F) f += 360.0F;
        return f;
    }

    public static float angleDelta(float current, float prev) {
        return wrapDegrees(current - prev);
    }

    public float getYawDelta(int ticksAgo) {
        if (history.size() <= ticksAgo) return 0.0f;
        return angleDelta(history.get(0).yaw, history.get(ticksAgo).yaw);
    }

    public float getPitchDelta(int ticksAgo) {
        if (history.size() <= ticksAgo) return 0.0f;
        return angleDelta(history.get(0).pitch, history.get(ticksAgo).pitch);
    }

    public float getYawAcceleration() {
        if (history.size() < 3) return 0.0f;
        float d1 = getYawDelta(1);
        float dPrev = angleDelta(history.get(1).yaw, history.get(2).yaw);
        return d1 - dPrev;
    }

    public float getPitchAcceleration() {
        if (history.size() < 3) return 0.0f;
        float d1 = getPitchDelta(1);
        float dPrev = angleDelta(history.get(1).pitch, history.get(2).pitch);
        return d1 - dPrev;
    }

    public static float calculateAngleOffset(Location eyeLoc, Location targetLoc) {
        Vector toTarget = targetLoc.toVector().subtract(eyeLoc.toVector()).normalize();
        Vector lookDir = eyeLoc.getDirection().normalize();
        double dot = Math.max(-1.0, Math.min(1.0, lookDir.dot(toTarget)));
        return (float) Math.toDegrees(Math.acos(dot));
    }
}
