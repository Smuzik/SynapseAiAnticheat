package net.synapselabs.anticheat.engine;

import net.synapselabs.anticheat.AiAnticheatPlugin;
import net.synapselabs.anticheat.data.PlayerProfile;
import net.synapselabs.anticheat.data.ThreatState;
import net.synapselabs.anticheat.engine.RiskAssessment.Contribution;
import net.synapselabs.anticheat.tracker.CombatTracker;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Controlled asynchronous service. This is where the ACTUAL verdict is made, because the AI probability is
 * only available here (off the main thread). The flow is exactly the mandated architecture:
 *
 * <pre>{signals} + context + model P(cheat) → RiskEngine.contributions → eventRisk
 *        → RiskAccumulator.observe (temporal) → RiskEngine.assess → verdict → DetectionSnapshot</pre>
 *
 * <p>The ONNX model is folded in as ONE weighted contribution (never the gate). A verdict of CHEAT is the
 * only thing that ever populates a violation lane, so downstream {@code PunishmentManager} /
 * {@code PlayerProfile} keep working unchanged while never punishing on SUSPICIOUS or a single event.
 */
public class InferenceService implements AutoCloseable {
    private final AiAnticheatPlugin plugin;
    private final AiInferenceEngine engine;
    private final RiskEngine riskEngine;
    private final ExecutorService executor;
    private final List<Consumer<DetectionSnapshot>> subscribers = new ArrayList<>();

    public InferenceService(AiAnticheatPlugin plugin, AiInferenceEngine engine) {
        this.plugin = plugin;
        this.engine = engine;

        double suspicious = plugin.getConfig()
                .getDouble("detection.calibration.suspicious_threshold", RiskEngine.DEFAULT_SUSPICIOUS);
        double cheat = plugin.getConfig()
                .getDouble("detection.calibration.cheat_threshold", RiskEngine.DEFAULT_CHEAT);
        this.riskEngine = new RiskEngine(suspicious, cheat);

        this.executor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "SynapseAI-InferenceWorker");
            t.setDaemon(true);
            return t;
        });
    }

    public void subscribe(Consumer<DetectionSnapshot> listener) {
        subscribers.add(listener);
    }

    /**
     * Submit a combat event for scoring. {@code signals} and {@code context} come from
     * {@link HardCombatChecks#evaluate}; the model probability is computed here and added as one signal.
     */
    public void submitAnalysis(Player player, FeatureVector vector, CombatTracker tracker,
                               List<Signal> signals, CombatContext context) {
        if (player == null || !player.isOnline()) return;
        UUID playerId = player.getUniqueId();
        String playerName = player.getName();

        executor.submit(() -> {
            try {
                // --- AI model: ONE signal, low trust, computed here where it is available ---
                float modelP = 0.0f;
                try {
                    float[] input = vector.getInferenceInput16();
                    AiInferenceEngine.PredictionResult result = engine.predict(input);
                    modelP = result.cheatProbability();
                } catch (Throwable t) {
                    // A model failure must NEVER manufacture suspicion — treat as zero contribution.
                    plugin.getLogger().warning("[AI Inference] Model execution failed: " + t.getClass().getSimpleName() + ": " + t.getMessage());
                }

                // --- Risk Engine: signals → contributions → event risk → temporal aggregation → verdict ---
                List<Contribution> contribs = riskEngine.contributions(signals, context, modelP);
                double eventRisk = riskEngine.eventRisk(contribs);
                long now = System.currentTimeMillis();
                boolean hasEvidence = !signals.isEmpty() || modelP >= 0.80f;
                double aggregated = tracker.getRiskAccumulator().observe(eventRisk, now, hasEvidence);
                RiskAssessment assessment = riskEngine.assess(signals, context, modelP, aggregated);
                tracker.setLastAssessment(assessment);

                String verdict = assessment.verdict().name();
                float riskScore01 = (float) (assessment.risk() / RiskEngine.RISK_CLAMP);
                ThreatState state = threatStateFor(assessment.verdict());

                // Project the verdict onto the legacy confidence fields (overhead display / decay loop).
                tracker.applyRisk(riskScore01, state);

                List<String> reasons = buildReasons(assessment);

                // Populate a violation lane ONLY when actionable (CHEAT). SUSPICIOUS alerts but never punishes.
                List<String> hardViolations = List.of();
                List<String> aiViolations = List.of();
                if (assessment.isActionable()) {
                    Contribution dominant = dominantSignal(assessment);
                    String label = dominant != null ? label(dominant.signal()) : "Combat";
                    if (dominant != null && dominant.signal() == SignalType.REACH) {
                        hardViolations = List.of(label);
                    } else {
                        aiViolations = List.of(label);
                    }
                }

                if (plugin.getConfig().getBoolean("logging.console.verbose", false)) {
                    plugin.getLogger().info(String.format(
                        "[Risk] %s | model=%.2f | event=%.2f | risk=%.1f | %s | %s",
                        playerName, modelP, eventRisk, assessment.risk(), verdict, reasons));
                }

                Map<String, Object> metadata = new LinkedHashMap<>();
                metadata.put("verdict", verdict);
                metadata.put("risk", assessment.risk());
                metadata.put("event_risk", eventRisk);
                metadata.put("model_p", (double) modelP);
                metadata.put("schema", FeatureVector.CURRENT_VERSION);

                DetectionSnapshot snapshot = DetectionSnapshot.builder(playerId, playerName)
                    .rawScore(modelP)
                    .confidence(riskScore01)
                    .suspicion(tracker.getSuspicion())
                    .killauraConfidence(riskScore01)
                    .aimConfidence(riskScore01)
                    .threatState(state)
                    .hardViolations(hardViolations)
                    .aiViolations(aiViolations)
                    .featureVector(vector)
                    .metadata(metadata)
                    .verdict(verdict)
                    .riskScore(riskScore01)
                    .reasons(reasons)
                    .build();

                // Dispatch snapshot to subscribers on the Bukkit main thread.
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player p = Bukkit.getPlayer(playerId);
                    if (p != null && p.isOnline()) {
                        PlayerProfile profile = plugin.getDataManager().getOrCreate(p);
                        profile.applySnapshot(snapshot);

                        for (Consumer<DetectionSnapshot> sub : subscribers) {
                            try {
                                sub.accept(snapshot);
                            } catch (Throwable t) {
                                plugin.getLogger().warning("Error in detection snapshot subscriber: " + t.getMessage());
                            }
                        }
                    }
                });
            } catch (Throwable t) {
                plugin.getLogger().warning("Failed during async inference analysis: " + t.getMessage());
            }
        });
    }

    private static ThreatState threatStateFor(RiskAssessment.Verdict verdict) {
        return switch (verdict) {
            case CHEAT -> ThreatState.CONFIRMED;
            case SUSPICIOUS -> ThreatState.SUSPICIOUS;
            case LEGIT -> ThreatState.CLEAN;
        };
    }

    /** The largest non-AI contribution — the signal a staff member should look at first. */
    private static Contribution dominantSignal(RiskAssessment assessment) {
        return assessment.contributions().stream()
                .filter(c -> c.signal() != SignalType.AI_MODEL)
                .max(Comparator.comparingDouble(Contribution::contribution))
                .orElse(null);
    }

    /** Top few contributors as readable strings, e.g. "Reach 42%" — share of the event's risk. */
    private static List<String> buildReasons(RiskAssessment assessment) {
        double total = 0.0;
        for (Contribution c : assessment.contributions()) total += Math.max(0.0, c.contribution());
        double denom = total <= 0.0 ? 1.0 : total;

        List<Contribution> sorted = new ArrayList<>(assessment.contributions());
        sorted.sort(Comparator.comparingDouble(Contribution::contribution).reversed());

        List<String> reasons = new ArrayList<>();
        for (Contribution c : sorted) {
            if (c.contribution() < 0.5) continue;
            int share = (int) Math.round(c.contribution() / denom * 100.0);
            reasons.add(label(c.signal()) + " " + share + "%");
            if (reasons.size() >= 3) break;
        }
        if (reasons.isEmpty()) reasons.add("Clean");
        return reasons;
    }

    private static String label(SignalType type) {
        return switch (type) {
            case HARD_SNAP -> "Aim snap";
            case HITBOX_MISS -> "Hitbox miss";
            case REACH -> "Reach";
            case AIM_CONSISTENCY -> "Superhuman aim";
            case KINEMATIC_ROBOTIC -> "Robotic rotation";
            case SILENT_ROTATION -> "Silent rotation";
            case UNNATURAL_JERK -> "Unnatural jerk";
            case PERFECT_COOLDOWN_SYNC -> "Cooldown lock";
            case AI_MODEL -> "AI model";
        };
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(1000, TimeUnit.MILLISECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }
}
