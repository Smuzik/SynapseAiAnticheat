package net.synapselabs.anticheat.engine;

import java.util.List;
import java.util.Map;

/**
 * The explainable output of a {@link RiskEngine} evaluation: a final verdict, the numeric risk, and a
 * per-signal breakdown. The breakdown is what the admin dashboard renders as
 * "Aim 89% / Rotation 31% / Combat 76% / Context adjustment -18% / Final Risk 82%".
 */
public record RiskAssessment(
    Verdict verdict,
    double risk,        // aggregated risk score (temporal), clamped to [0, RISK_CLAMP]
    double eventRisk,   // this single event's risk before temporal aggregation
    List<Contribution> contributions
) {
    public enum Verdict { LEGIT, SUSPICIOUS, CHEAT }

    /**
     * One signal's contribution to the risk.
     *
     * @param modifiers ordered map of context modifier name -&gt; factor that was applied
     * @param contribution baseWeight * confidence * product(modifiers)
     */
    public record Contribution(
        SignalType signal,
        double base,
        double confidence,
        Map<String, Double> modifiers,
        double contribution
    ) {}

    public boolean isActionable() {
        return verdict == Verdict.CHEAT;
    }

    public int riskPercent() {
        return (int) Math.round(Math.max(0.0, Math.min(100.0, risk)));
    }
}
