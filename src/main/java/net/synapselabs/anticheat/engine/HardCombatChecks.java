package net.synapselabs.anticheat.engine;

import net.synapselabs.anticheat.AiAnticheatPlugin;
import net.synapselabs.anticheat.tracker.CombatTracker;
import net.synapselabs.anticheat.tracker.KinematicHistory;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * Hard Combat Checks — TRANSFORMED (Phase 1).
 *
 * <p>Previously this ran "hard check → cancel damage → punish" synchronously, which produced the headline
 * false positives (a legit 360° corner-crit flick was kicked as killaura). It now does
 * "hard SIGNAL → build context": it converts the same fast, deterministic measurements into {@link Signal}s
 * and a {@link CombatContext}, and hands them to the {@link RiskEngine} (in the async path) which makes the
 * ACTUAL decision. The only thing decided here is a protective, reversible damage-cancel — and only when
 * lag-compensated reach is CERTAINLY impossible. Nothing here punishes.
 *
 * <p>Runs on the main thread (called from the damage event), so world/block access for corner detection is
 * safe.
 */
public class HardCombatChecks {
    private final AiAnticheatPlugin plugin;

    /** The transformed result: signals + context for the Risk Engine, plus a protective cancel flag. */
    public record HardCheckResult(
        List<Signal> signals,
        CombatContext context,
        boolean cancelDamage,
        double calculatedReach,   // lag-compensated
        float angleOffsetDeg,
        boolean rayHit
    ) {}

    public HardCombatChecks(AiAnticheatPlugin plugin) {
        this.plugin = plugin;
    }

    public HardCheckResult evaluate(Player attacker, LivingEntity victim, CombatTracker tracker,
                                    KinematicHistory.RaycastHitResult raycast, double compensatedReach) {
        KinematicHistory history = tracker.getHistory();

        double maxReachConfig = plugin.getConfig().getDouble("detection.hard_checks.max_reach", 3.25);
        double reachHardFlag = plugin.getConfig().getDouble("detection.hard_checks.reach_hard_flag", 3.65);
        double fovOuterGate = plugin.getConfig().getDouble("detection.hard_checks.fov_outer_deg", 70.0);

        float angleOffset = raycast.angleOffsetDeg();
        boolean rayHit = raycast.hit();

        // --- Kinematics (SIGNED — direction matters) ---
        float yawDelta5t = history.getYawDelta(5);
        float yawAccelSigned = history.getYawAcceleration();
        float angularVel = history.getAngularVelocity();
        float yawAccelAbs = Math.abs(yawAccelSigned);
        long timeSinceRot = history.getTimeSinceLastRotation();

        // A mechanical snap: impossible speed + no deceleration tail + snapped PAST the hitbox.
        boolean hardSnap = angularVel > 90.0f && timeSinceRot <= 30L && yawAccelAbs > 75.0f
                && angleOffset < 5.0f && !rayHit;
        // The robotic signature that drives temporal repetition (killaura keeps accelerating onto target).
        boolean roboticSnap = Math.abs(yawDelta5t) > 100.0f && yawAccelSigned > 40.0f && angleOffset < 5.0f;

        // --- Context (biased benign: when uncertain we REDUCE suspicion) ---
        int solidSides = countSolidSides(victim.getLocation());
        boolean nearWall = solidSides >= 1;
        boolean inCorner = solidSides >= 2;
        boolean isCrit = isCritical(attacker);
        boolean victimKnockback = isBeingKnockedBack(victim);
        int ping = safePing(attacker);

        CombatTracker.PatternContext pat = tracker.updatePattern(victim.getUniqueId(), roboticSnap);

        CombatContext context = CombatContext.builder()
                .inCorner(inCorner)
                .nearWall(nearWall)
                .victimKnockback(victimKnockback)
                .pingMs(ping)
                .isCrit(isCrit)
                .targetSwitch(pat.targetSwitch())
                .repeatedPattern(pat.repeatedPattern())
                .yawAccel(yawAccelSigned)
                .build();

        // --- Lag-compensated reach excess & protective cancel ---
        double excess = ContextEngine.lagCompensatedReachExcess(compensatedReach, context);
        boolean cancelDamage = compensatedReach >= reachHardFlag && excess > 0.0;

        // --- Signals ---
        List<Signal> signals = new ArrayList<>();
        if (compensatedReach > 3.08 || (raycast.distance() > 3.25 && excess > 0.02)) {
            signals.add(Signal.of(SignalType.REACH, SignalType.REACH.defaultConfidence(),
                    compensatedReach, String.format("reach %.2fm (lag-comp)", compensatedReach)));
        }
        if ((!rayHit && angleOffset > 25.0f && raycast.distance() >= 1.5) || (angleOffset > 35.0f && raycast.distance() >= 1.5)) {
            signals.add(Signal.of(SignalType.HITBOX_MISS, SignalType.HITBOX_MISS.defaultConfidence(),
                    angleOffset, String.format("ray miss @ %.1f° (dist %.1fm)", angleOffset, raycast.distance())));
        }
        if ((angularVel > 12.0f && yawAccelAbs >= 14.0f && timeSinceRot <= 50L) || (yawAccelAbs >= 18.0f)) {
            signals.add(Signal.of(SignalType.HARD_SNAP, SignalType.HARD_SNAP.defaultConfidence(),
                    angularVel, String.format("snap %.0f°/t accel %.0f", angularVel, yawAccelAbs)));
        }
        // Derived Aim / Rotation signals from the signed kinematics
        RiskEngine.addDerivedSignals(signals, angleOffset, angleOffset, yawDelta5t, yawAccelSigned, compensatedReach);

        return new HardCheckResult(signals, context, cancelDamage, compensatedReach, angleOffset, rayHit);
    }

    // --- context helpers -------------------------------------------------------------------------

    /** Count solid blocks on the 4 horizontal sides at the victim's torso — a corner proxy. */
    private static int countSolidSides(Location feet) {
        Location torso = feet.clone().add(0, 1, 0);
        int count = 0;
        for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST}) {
            try {
                if (torso.getBlock().getRelative(face).getType().isSolid()) count++;
            } catch (Throwable ignored) {}
        }
        return count;
    }

    /** Approximate vanilla critical-hit conditions (falling, airborne, full charge, not in water/vehicle). */
    private static boolean isCritical(Player attacker) {
        try {
            boolean airborneFalling = attacker.getFallDistance() > 0.0f && !attacker.isOnGround();
            boolean fullCharge = attacker.getAttackCooldown() > 0.9f;
            boolean grounded = attacker.isInWater() || attacker.isInsideVehicle();
            return airborneFalling && fullCharge && !grounded;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** True if the victim is moving fast horizontally (recently knocked back / in motion). */
    private static boolean isBeingKnockedBack(LivingEntity victim) {
        try {
            Vector v = victim.getVelocity();
            double horiz = Math.sqrt(v.getX() * v.getX() + v.getZ() * v.getZ());
            return horiz > 0.08;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static int safePing(Player attacker) {
        try {
            return attacker.getPing();
        } catch (Throwable ignored) {
            return 0;
        }
    }
}
