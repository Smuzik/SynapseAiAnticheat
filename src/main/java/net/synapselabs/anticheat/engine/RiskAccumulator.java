package net.synapselabs.anticheat.engine;

/**
 * Per-player temporal risk state. One instance per online player; the runtime feeds each combat event's
 * risk in and reads back the aggregated, time-decayed risk that the verdict is drawn from.
 *
 * <p>Two temporal models coexist deliberately:
 * <ul>
 *   <li><b>Offline / tests</b> use {@link RiskEngine#aggregateRepeated(double)} — a deterministic 6×
 *       replay through the {@code 0.85} decay — so scenario fixtures produce fixed, assertable numbers.</li>
 *   <li><b>Runtime</b> uses this class: exponential decay by wall-clock time between events, then the new
 *       event's risk is added. Real repetition = several events close together, whose risk therefore
 *       stacks toward CHEAT; a one-off flick decays away over the half-life and never escalates.</li>
 * </ul>
 *
 * <p>All mutating methods are synchronized: combat events may arrive on the main thread while the async
 * inference path reads current risk.
 */
public final class RiskAccumulator {

    /** Time for accumulated risk to halve when a player stops triggering signals. */
    public static final long DEFAULT_HALF_LIFE_MILLIS = 30_000L;

    private final long halfLifeMillis;
    private double risk;
    private long lastUpdate; // 0 == never updated

    public RiskAccumulator() {
        this(DEFAULT_HALF_LIFE_MILLIS);
    }

    public RiskAccumulator(long halfLifeMillis) {
        if (halfLifeMillis <= 0) throw new IllegalArgumentException("halfLifeMillis must be > 0");
        this.halfLifeMillis = halfLifeMillis;
    }

    /** Decay to {@code nowMillis}, add this event's risk, clamp, and return the new aggregated risk. */
    public synchronized double observe(double eventRisk, long nowMillis) {
        return observe(eventRisk, nowMillis, true);
    }

    /** Decay to {@code nowMillis}, apply risk with structural signal awareness, clamp, and return the new aggregated risk. */
    public synchronized double observe(double eventRisk, long nowMillis, boolean hasStructuralSignals) {
        decayTo(nowMillis);
        if (hasStructuralSignals) {
            risk = RiskEngine.clamp(risk + eventRisk);
        } else {
            // When there are NO structural signals (clean hit), the event risk is purely AI model estimate.
            // Clean hits actively decay existing accumulated risk and cap pure AI baseline at 15.0 (below SUSPICIOUS).
            risk = Math.max(0.0, risk * 0.90);
            double aiCap = Math.min(15.0, eventRisk);
            risk = Math.max(risk, aiCap);
        }
        lastUpdate = nowMillis;
        return risk;
    }

    /** Current aggregated risk, decayed to {@code nowMillis} without adding anything. */
    public synchronized double current(long nowMillis) {
        decayTo(nowMillis);
        return risk;
    }

    public synchronized void reset() {
        risk = 0.0;
        lastUpdate = 0L;
    }

    private void decayTo(long nowMillis) {
        if (lastUpdate == 0L) {
            lastUpdate = nowMillis;
            return;
        }
        long dt = nowMillis - lastUpdate;
        if (dt <= 0L) return;
        double factor = Math.pow(0.5, dt / (double) halfLifeMillis);
        risk *= factor;
        lastUpdate = nowMillis;
    }
}
