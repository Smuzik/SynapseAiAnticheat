package net.synapselabs.anticheat.engine;

/**
 * A single piece of evidence fed to the {@link RiskEngine}. Immutable.
 *
 * @param type       which detector produced it
 * @param baseWeight the raw weight before context modifiers/confidence (usually {@link SignalType#baseWeight()})
 * @param confidence how sure this detector is, in [0,1]. For {@link SignalType#REACH} this is derived
 *                   from lag compensation; for {@link SignalType#AI_MODEL} it is the model trust.
 * @param value      the raw measured value (e.g. reach distance in blocks), for explainability; may be NaN
 * @param detail     short human-readable note for logs/GUI (e.g. "3.42m")
 */
public record Signal(
    SignalType type,
    double baseWeight,
    double confidence,
    double value,
    String detail
) {
    public static Signal of(SignalType type) {
        return new Signal(type, type.baseWeight(), type.defaultConfidence(), Double.NaN, null);
    }

    public static Signal of(SignalType type, double confidence, double value, String detail) {
        return new Signal(type, type.baseWeight(), confidence, value, detail);
    }
}
