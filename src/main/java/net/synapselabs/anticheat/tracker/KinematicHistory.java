package net.synapselabs.anticheat.tracker;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedList;

public class KinematicHistory {
    public record Snapshot(float yaw, float pitch, double x, double y, double z, long time) {}

    private final LinkedList<Snapshot> history = new LinkedList<>();
    private static final int MAX_HISTORY = 30;

    private final Deque<Long> attackTimestamps = new ArrayDeque<>();
    private final Deque<Double> distanceHistory = new ArrayDeque<>();
    private static final int STATS_WINDOW = 10;

    private long lastRotationTimestamp = 0;
    private long lastAttackTimestamp = 0;

    public void push(Location loc) {
        long now = System.currentTimeMillis();
        if (!history.isEmpty()) {
            Snapshot last = history.peekFirst();
            if (last != null && (Math.abs(loc.getYaw() - last.yaw) > 0.01f || Math.abs(loc.getPitch() - last.pitch) > 0.01f)) {
                this.lastRotationTimestamp = now;
            }
        }

        history.addFirst(new Snapshot(
            loc.getYaw(),
            loc.getPitch(),
            loc.getX(),
            loc.getY(),
            loc.getZ(),
            now
        ));
        while (history.size() > MAX_HISTORY) {
            history.removeLast();
        }
    }

    public void registerAttack(double distance) {
        long now = System.currentTimeMillis();
        this.lastAttackTimestamp = now;

        attackTimestamps.addFirst(now);
        while (attackTimestamps.size() > STATS_WINDOW) {
            attackTimestamps.removeLast();
        }

        distanceHistory.addFirst(distance);
        while (distanceHistory.size() > STATS_WINDOW) {
            distanceHistory.removeLast();
        }
    }

    public float getAttackIntervalTicks() {
        if (attackTimestamps.size() < 2) return 10.0f;
        Long[] arr = attackTimestamps.toArray(new Long[0]);
        long deltaMs = arr[0] - arr[1];
        return Math.max(1.0f, (float) (deltaMs / 50.0));
    }

    public double getMeanReach() {
        if (distanceHistory.isEmpty()) return 3.0;
        double sum = 0.0;
        for (double d : distanceHistory) sum += d;
        return sum / distanceHistory.size();
    }

    public double getReachVariance() {
        if (distanceHistory.size() < 2) return 0.0;
        double mean = getMeanReach();
        double sum = 0.0;
        for (double d : distanceHistory) {
            sum += Math.pow(d - mean, 2);
        }
        return sum / distanceHistory.size();
    }

    public long getLastAttackTimestamp() {
        return lastAttackTimestamp;
    }

    public long getLastRotationTimestamp() {
        return lastRotationTimestamp;
    }

    public long getTimeSinceLastRotation() {
        return System.currentTimeMillis() - lastRotationTimestamp;
    }

    public static float wrapDegrees(float f) {
        f %= 360.0F;
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

    public float getYawJerk() {
        if (history.size() < 4) return 0.0f;
        float aCurrent = getYawAcceleration();
        float dPrev1 = angleDelta(history.get(1).yaw, history.get(2).yaw);
        float dPrev2 = angleDelta(history.get(2).yaw, history.get(3).yaw);
        float aPrev = dPrev1 - dPrev2;
        return aCurrent - aPrev;
    }

    public float getPitchJerk() {
        if (history.size() < 4) return 0.0f;
        float aCurrent = getPitchAcceleration();
        float dPrev1 = angleDelta(history.get(1).pitch, history.get(2).pitch);
        float dPrev2 = angleDelta(history.get(2).pitch, history.get(3).pitch);
        float aPrev = dPrev1 - dPrev2;
        return aCurrent - aPrev;
    }

    public float getRotationVariance10t() {
        int window = Math.min(10, history.size() - 1);
        if (window < 3) return 5.0f;

        float[] speeds = new float[window];
        float sum = 0.0f;
        for (int i = 0; i < window; i++) {
            float yd = Math.abs(angleDelta(history.get(i).yaw, history.get(i + 1).yaw));
            float pd = Math.abs(angleDelta(history.get(i).pitch, history.get(i + 1).pitch));
            speeds[i] = (float) Math.sqrt(yd * yd + pd * pd);
            sum += speeds[i];
        }
        float mean = sum / window;
        float sumSq = 0.0f;
        for (int i = 0; i < window; i++) {
            float diff = speeds[i] - mean;
            sumSq += diff * diff;
        }
        return (float) Math.sqrt(sumSq / window);
    }

    public float getAngularVelocity() {
        float yd = Math.abs(getYawDelta(1));
        float pd = Math.abs(getPitchDelta(1));
        return (float) Math.sqrt(yd * yd + pd * pd);
    }

    public static float calculateAngleOffset(Location eyeLoc, Location targetCenterLoc) {
        Vector toTarget = targetCenterLoc.toVector().subtract(eyeLoc.toVector()).normalize();
        Vector lookDir = eyeLoc.getDirection().normalize();
        double dot = Math.max(-1.0, Math.min(1.0, lookDir.dot(toTarget)));
        return (float) Math.toDegrees(Math.acos(dot));
    }

    /**
     * Accurate Hitbox Raycast test: checks whether the attacker's line of sight intersects the
     * target's expanded bounding box (with latency compensation / margin).
     * If the ray misses, calculates the true minimum distance to the closest point of the bounding box
     * and computes hitboxEdgeProximity.
     */
    public static RaycastHitResult raycastTargetHitbox(Location eyeLoc, LivingEntity victim, double latencyMargin, double maxDistance) {
        Vector origin = eyeLoc.toVector();
        Vector direction = eyeLoc.getDirection().normalize();

        BoundingBox box = victim.getBoundingBox().clone().expand(latencyMargin);
        Vector center = box.getCenter();
        float angleOffset = calculateAngleOffset(eyeLoc, center.toLocation(victim.getWorld()));

        RayTraceResult result = box.rayTrace(origin, direction, maxDistance);
        if (result != null && result.getHitPosition() != null) {
            Vector hitPos = result.getHitPosition();
            double hitDist = origin.distance(hitPos);

            double hx = Math.max(1e-4, box.getWidthX() / 2.0);
            double hy = Math.max(1e-4, box.getHeight() / 2.0);
            double hz = Math.max(1e-4, box.getWidthZ() / 2.0);

            double nx = Math.abs(hitPos.getX() - center.getX()) / hx;
            double ny = Math.abs(hitPos.getY() - center.getY()) / hy;
            double nz = Math.abs(hitPos.getZ() - center.getZ()) / hz;
            float edgeProximity = (float) Math.min(1.0, Math.max(nx, Math.max(ny, nz)));

            return new RaycastHitResult(true, (float) hitDist, angleOffset, edgeProximity);
        }

        // Ray missed the bounding box: compute closest point on box surface
        double clampedX = Math.max(box.getMinX(), Math.min(box.getMaxX(), origin.getX()));
        double clampedY = Math.max(box.getMinY(), Math.min(box.getMaxY(), origin.getY()));
        double clampedZ = Math.max(box.getMinZ(), Math.min(box.getMaxZ(), origin.getZ()));
        double minDistanceToBox = origin.distance(new Vector(clampedX, clampedY, clampedZ));

        return new RaycastHitResult(false, (float) minDistanceToBox, angleOffset, 1.0f);
    }

    public record RaycastHitResult(boolean hit, float distance, float angleOffsetDeg, float hitboxEdgeProximity) {}
}
