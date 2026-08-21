package net.synapselabs.anticheat.engine;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Real lag compensation: keeps a short position history per potential victim so a reach reading can be
 * checked against where the victim ACTUALLY was in the attacker's rewound view, not where they are on the
 * server "now". This is what turns a "3.42m reach" during a 150ms ping spike + knockback into a legit hit.
 *
 * <p>Positions are recorded on movement (see {@code CombatListener.onMove}); mobs that never fire a move
 * event simply fall back to their live position, which degrades gracefully to the naive (uncompensated)
 * reach. Combat between players — where reach cheating actually happens — is fully covered.
 *
 * <p>This refines the REACH signal's VALUE before it reaches the Risk Engine; the engine's own
 * {@link ContextEngine#reachConfidence} then applies on top, so the runtime is strictly more forgiving
 * than the unit-tested scalar model (which is exactly the bias we want for eliminating false positives).
 */
public final class LagCompensator {

    /** One recorded pose of a victim: bounding-box center + half-extents at a moment in time. */
    public record Pose(double cx, double cy, double cz, double hx, double hy, double hz, long time) {}

    /** Bug #6 fix: increased from 40 to 60 (~3s at 20 tps) to cover high-ping rewind windows. */
    private static final int MAX_SAMPLES = 60;
    private static final long MAX_AGE_MS = 3_000L;

    private final Map<UUID, Deque<Pose>> history = new ConcurrentHashMap<>();

    /** Record the entity's current hitbox. Cheap; call on movement and on being hit. */
    public void record(Entity entity) {
        if (entity == null) return;
        BoundingBox box = entity.getBoundingBox();
        Pose pose = new Pose(
                box.getCenterX(), box.getCenterY(), box.getCenterZ(),
                box.getWidthX() / 2.0, box.getHeight() / 2.0, box.getWidthZ() / 2.0,
                System.currentTimeMillis());
        Deque<Pose> q = history.computeIfAbsent(entity.getUniqueId(), k -> new ArrayDeque<>());
        synchronized (q) {
            q.addFirst(pose);
            while (q.size() > MAX_SAMPLES) q.removeLast();
        }
    }

    public void forget(UUID id) {
        history.remove(id);
    }

    /**
     * The fairest reach for this hit: the minimum eye-to-hitbox distance over the rewound window
     * {@code [now - ping - jitter, now - ping + jitter]}. Falls back to {@code naiveReach} when no
     * usable history exists (e.g. mobs, or the victim just spawned).
     *
     * <p>Bug #6 fix: jitter window is now dynamic — {@code max(60, pingMs * 0.3)} — so high-ping
     * players (200+ ms) get a wider search window that matches their actual network variance.
     */
    public double compensatedReach(Location eyeLoc, UUID victimId, int pingMs, double naiveReach) {
        Deque<Pose> q = history.get(victimId);
        if (q == null) return naiveReach;

        long now = System.currentTimeMillis();
        long targetAge = Math.max(0L, pingMs);
        // Bug #6: dynamic jitter window — scales with ping instead of fixed 60ms
        long jitter = Math.max(60L, (long) (pingMs * 0.3));
        Vector eye = eyeLoc.toVector();

        double best = naiveReach;
        boolean matched = false;
        Pose[] snapshot;
        synchronized (q) {
            snapshot = q.toArray(new Pose[0]);
        }
        for (Pose p : snapshot) {
            long age = now - p.time();
            if (age > MAX_AGE_MS) break;                       // deque is newest-first; rest are older
            if (Math.abs(age - targetAge) > jitter) continue;
            double d = distanceToBox(eye, p);
            if (!matched || d < best) {
                best = d;
                matched = true;
            }
        }
        // Never report MORE than the naive reach — compensation can only ever help the defender.
        return matched ? Math.min(best, naiveReach) : naiveReach;
    }

    private static double distanceToBox(Vector eye, Pose p) {
        double dx = Math.max(0.0, Math.abs(eye.getX() - p.cx()) - p.hx());
        double dy = Math.max(0.0, Math.abs(eye.getY() - p.cy()) - p.hy());
        double dz = Math.max(0.0, Math.abs(eye.getZ() - p.cz()) - p.hz());
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
