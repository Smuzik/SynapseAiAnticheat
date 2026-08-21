package net.synapselabs.anticheat.tracker;

import net.synapselabs.anticheat.data.ThreatState;
import net.synapselabs.anticheat.engine.FeatureVector;
import net.synapselabs.anticheat.engine.RiskAccumulator;
import net.synapselabs.anticheat.engine.RiskAssessment;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.UUID;

public class CombatTracker {
    private final KinematicHistory history = new KinematicHistory();

    /** Per-player temporal risk state — the runtime aggregator the Risk Engine reads/writes. */
    private final RiskAccumulator riskAccumulator = new RiskAccumulator();
    private volatile RiskAssessment lastAssessment;

    // --- Pattern state for CombatContext (target-switching + repeated robotic snaps) ---
    /** A target switch only counts if it happens within this window of the previous hit. */
    private static final long TARGET_SWITCH_WINDOW_MS = 800L;
    /** Consecutive robotic snaps must land within this gap to keep the streak alive. */
    private static final long ROBOTIC_STREAK_GAP_MS = 1500L;
    /** How many stacked robotic snaps constitute a "repeated pattern" (the ×1.5 amplifier). */
    private static final int REPEATED_PATTERN_MIN = 4;

    private UUID lastVictimId;
    private long lastVictimTime;
    private int roboticStreak;
    private long lastRoboticTime;

    private float rawScore = 0.0f;
    private float confidence = 0.0f;
    private float killauraConfidence = 0.0f;
    private float aimConfidence = 0.0f;
    private float suspicion = 0.0f;
    private ThreatState threatState = ThreatState.CLEAN;

    private int suspiciousStreak = 0;
    private long lastAnalysisTimestamp = 0;
    private long lastDecayTimestamp = System.currentTimeMillis();

    public KinematicHistory getHistory() {
        return history;
    }

    public RiskAccumulator getRiskAccumulator() {
        return riskAccumulator;
    }

    public void setLastAssessment(RiskAssessment assessment) {
        this.lastAssessment = assessment;
    }

    public RiskAssessment getLastAssessment() {
        return lastAssessment;
    }

    /** The two stateful context flags — computed from the victim identity + whether this hit was robotic. */
    public record PatternContext(boolean targetSwitch, boolean repeatedPattern) {}

    /**
     * Update per-player pattern state for a new combat event and return the derived context flags.
     * {@code roboticSnap} should be the mechanical "killaura signature" (large turn still accelerating);
     * a genuine streak of these is what unlocks the {@code repeatedPattern} amplifier.
     */
    public synchronized PatternContext updatePattern(UUID victimId, boolean roboticSnap) {
        long now = System.currentTimeMillis();

        boolean targetSwitch = lastVictimId != null
                && !lastVictimId.equals(victimId)
                && (now - lastVictimTime) < TARGET_SWITCH_WINDOW_MS;
        lastVictimId = victimId;
        lastVictimTime = now;

        if (roboticSnap) {
            roboticStreak = (now - lastRoboticTime <= ROBOTIC_STREAK_GAP_MS) ? roboticStreak + 1 : 1;
            lastRoboticTime = now;
        } else if (now - lastRoboticTime > ROBOTIC_STREAK_GAP_MS) {
            roboticStreak = 0;
        }

        boolean repeatedPattern = roboticStreak >= REPEATED_PATTERN_MIN;
        return new PatternContext(targetSwitch, repeatedPattern);
    }

    /**
     * Apply a finished Risk Engine assessment to the legacy confidence fields so the existing overhead
     * display, decay loop and threat colouring keep working. The Risk Engine — not this EMA — is now the
     * source of truth for the verdict; these fields are a projection of {@code riskScore} for display.
     */
    public synchronized void applyRisk(float riskScore01, ThreatState state) {
        float clamped = Math.max(0.0f, Math.min(1.0f, riskScore01));
        this.rawScore = clamped;
        this.confidence = clamped;
        this.killauraConfidence = clamped;
        this.aimConfidence = clamped;
        this.suspicion = Math.max(this.suspicion, clamped);
        this.threatState = state;
        this.lastAnalysisTimestamp = System.currentTimeMillis();
    }

    public FeatureVector createFeatureVector(Player attacker, Location eyeLoc, Location victimLoc, KinematicHistory.RaycastHitResult raycast) {
        // Canonical combat.v1 features, matching exactly how the collector wrote the training data:
        //   distance            : eye-to-eye distance
        //   angle_offset_deg    : angle between the look vector and the direction to the victim's EYE point
        //   raycast_hit         : whether the (lag-margin) ray intersected the hitbox
        //   raycast_distance    : the ray's hit distance (or distance to the box on a miss)
        //   raycast_angle_error : 0 on a clean hit, else the angular miss to the box centre (collector rule)
        float distance = (float) eyeLoc.distance(victimLoc);
        float angleOffsetToEye = KinematicHistory.calculateAngleOffset(eyeLoc, victimLoc);

        boolean rayHit = raycast.hit();
        float raycastDistance = raycast.distance();
        float raycastAngleError = rayHit ? 0.0f : raycast.angleOffsetDeg();

        // SIGNED kinematics — direction and (de)celeration must reach the model intact (no Math.abs).
        float yawDelta1t = history.getYawDelta(1);
        float yawDelta2t = history.getYawDelta(2);
        float yawDelta5t = history.getYawDelta(5);
        float yawAccel = history.getYawAcceleration();
        float pitchDelta1t = history.getPitchDelta(1);
        float pitchDelta2t = history.getPitchDelta(2);
        float pitchDelta5t = history.getPitchDelta(5);
        float pitchAccel = history.getPitchAcceleration();

        boolean isFalling = attacker.getFallDistance() > 0.0f && !attacker.isOnGround();
        boolean isSprinting = attacker.isSprinting();

        float cooldown = 1.0f;
        try {
            cooldown = attacker.getAttackCooldown();
        } catch (Throwable ignored) {}

        return FeatureVector.createV1(
            distance,
            angleOffsetToEye,
            yawDelta1t,
            yawDelta2t,
            yawDelta5t,
            yawAccel,
            pitchDelta1t,
            pitchDelta2t,
            pitchDelta5t,
            pitchAccel,
            rayHit,
            raycastDistance,
            raycastAngleError,
            isFalling,
            isSprinting,
            cooldown
        );
    }

    /**
     * Accumulates confidence gradually for subtle / closet cheats using Exponential Moving Average
     * and streak reinforcement (e.g. 35% -> 55% -> 82%).
     */
    public synchronized void processAiInference(float aiProbability, float minConfidenceThreshold) {
        this.rawScore = aiProbability;
        this.lastAnalysisTimestamp = System.currentTimeMillis();

        if (aiProbability >= minConfidenceThreshold) {
            suspiciousStreak++;
            // Dynamic alpha accumulation: rapid climb on repeated suspicious strikes
            float alpha = 0.40f + Math.min(0.35f, suspiciousStreak * 0.10f);
            this.killauraConfidence = (alpha * aiProbability) + ((1.0f - alpha) * this.killauraConfidence);
            this.aimConfidence = (alpha * (aiProbability * 0.95f)) + ((1.0f - alpha) * this.aimConfidence);

            // Increase independent historical suspicion
            this.suspicion = Math.min(1.0f, this.suspicion + 0.15f);
        } else {
            if (suspiciousStreak > 0) {
                suspiciousStreak--;
            }
            // Gentle per-hit decay (0.96x) so closet cheats with intermittent hits are not wiped instantly
            this.killauraConfidence = Math.max(0.0f, this.killauraConfidence * 0.96f);
            this.aimConfidence = Math.max(0.0f, this.aimConfidence * 0.96f);
        }

        this.confidence = Math.max(this.killauraConfidence, this.aimConfidence);
        this.threatState = ThreatState.fromConfidence(this.confidence);
    }

    public synchronized void registerHardViolation(String checkName) {
        this.rawScore = 1.0f;
        this.confidence = 0.98f;
        this.killauraConfidence = 0.98f;
        this.aimConfidence = 0.95f;
        this.suspicion = Math.min(1.0f, this.suspicion + 0.40f);
        this.threatState = ThreatState.CONFIRMED;
        this.suspiciousStreak += 3;
    }

    public synchronized void registerGrimViolation(String checkName, double vl) {
        this.lastAnalysisTimestamp = System.currentTimeMillis();
        this.suspicion = Math.min(1.0f, this.suspicion + 0.25f);
        this.suspiciousStreak += 2;

        String checkLower = checkName.toLowerCase();
        if (checkLower.contains("reach") || checkLower.contains("hitbox") || checkLower.contains("killaura") || checkLower.contains("aim")) {
            this.killauraConfidence = Math.max(0.80f, this.killauraConfidence + 0.25f);
            this.aimConfidence = Math.max(0.75f, this.aimConfidence + 0.20f);
            this.confidence = Math.max(this.killauraConfidence, this.aimConfidence);
            this.threatState = ThreatState.fromConfidence(this.confidence);
        } else {
            this.confidence = Math.max(this.confidence, 0.60f);
            if (this.threatState == ThreatState.CLEAN) {
                this.threatState = ThreatState.SUSPICIOUS;
            }
        }
    }

    public synchronized void decay(float amount) {
        long now = System.currentTimeMillis();
        // Only decay if player hasn't been in combat/flagged in the last 6 seconds
        if (now - lastAnalysisTimestamp > 6000L) {
            this.killauraConfidence = Math.max(0.0f, this.killauraConfidence - (amount * 0.3f));
            this.aimConfidence = Math.max(0.0f, this.aimConfidence - (amount * 0.3f));
            this.confidence = Math.max(this.killauraConfidence, this.aimConfidence);
            this.suspicion = Math.max(0.0f, this.suspicion - (amount * 0.5f));
            this.threatState = ThreatState.fromConfidence(this.confidence);
        }
        this.lastDecayTimestamp = now;
    }

    public synchronized void reset() {
        this.rawScore = 0.0f;
        this.confidence = 0.0f;
        this.killauraConfidence = 0.0f;
        this.aimConfidence = 0.0f;
        this.suspicion = 0.0f;
        this.threatState = ThreatState.CLEAN;
        this.suspiciousStreak = 0;
        this.roboticStreak = 0;
        this.lastRoboticTime = 0;
        this.lastVictimId = null;
        this.lastVictimTime = 0;
        this.riskAccumulator.reset();
        this.lastAssessment = null;
    }

    public float getRawScore() { return rawScore; }
    public float getConfidence() { return confidence; }
    public float getKillauraConfidence() { return killauraConfidence; }
    public float getAimConfidence() { return aimConfidence; }
    public float getSuspicion() { return suspicion; }
    public ThreatState getThreatState() { return threatState; }
    public int getSuspiciousStreak() { return suspiciousStreak; }
}
