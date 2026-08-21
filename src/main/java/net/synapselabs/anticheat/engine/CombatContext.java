package net.synapselabs.anticheat.engine;

/**
 * The situational context of a combat event, used by {@link ContextEngine} to compute multiplicative
 * modifiers on each signal. This is what lets the SAME raw signal mean "obviously legit" in one
 * situation and "suspicious" in another.
 *
 * <p>{@code yawAccel} is the SIGNED yaw acceleration at the hit — negative means the aim is
 * decelerating onto the target (a human flick brakes; a killaura snap does not).
 */
public record CombatContext(
    boolean inCorner,
    boolean nearWall,
    boolean victimKnockback,
    int pingMs,
    boolean isCrit,
    boolean targetSwitch,
    boolean repeatedPattern,
    double yawAccel
) {
    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private boolean inCorner, nearWall, victimKnockback, isCrit, targetSwitch, repeatedPattern;
        private int pingMs;
        private double yawAccel;

        public Builder inCorner(boolean v) { this.inCorner = v; return this; }
        public Builder nearWall(boolean v) { this.nearWall = v; return this; }
        public Builder victimKnockback(boolean v) { this.victimKnockback = v; return this; }
        public Builder pingMs(int v) { this.pingMs = v; return this; }
        public Builder isCrit(boolean v) { this.isCrit = v; return this; }
        public Builder targetSwitch(boolean v) { this.targetSwitch = v; return this; }
        public Builder repeatedPattern(boolean v) { this.repeatedPattern = v; return this; }
        public Builder yawAccel(double v) { this.yawAccel = v; return this; }

        public CombatContext build() {
            return new CombatContext(inCorner, nearWall, victimKnockback, pingMs,
                    isCrit, targetSwitch, repeatedPattern, yawAccel);
        }
    }
}
