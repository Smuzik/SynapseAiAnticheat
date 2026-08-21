package net.synapselabs.anticheat.data;

import net.synapselabs.anticheat.compat.CompatUtils;

public enum ThreatState {
    CLEAN("&#00ff88✔ &aЧИСТ", "&a✔ CLEAN", "&#00ff88", 0.0f, 0.39f),
    SUSPICIOUS("&#ffaa00⚠ &#ffcc00ПОДОЗРЕНИЕ", "&e⚠ SUSPICIOUS", "&#ffaa00", 0.40f, 0.69f),
    HIGH_CONFIDENCE("&#ff7700⚠ &#ffa500ВЫСОКИЙ РИСК", "&6⚠ HIGH CONFIDENCE", "&#ff7700", 0.70f, 0.89f),
    CONFIRMED("&#ff2244⚡ &#ff0055ПОДТВЕРЖДЕН (ЧИТ)", "&c⚡ CONFIRMED", "&#ff2244", 0.90f, 1.00f);

    private final String displayTag;
    private final String plainTag;
    private final String hexColor;
    private final float minConfidence;
    private final float maxConfidence;

    ThreatState(String displayTag, String plainTag, String hexColor, float minConfidence, float maxConfidence) {
        this.displayTag = displayTag;
        this.plainTag = plainTag;
        this.hexColor = hexColor;
        this.minConfidence = minConfidence;
        this.maxConfidence = maxConfidence;
    }

    public String getDisplayTag() {
        return CompatUtils.color(displayTag);
    }

    public String getPlainTag() {
        return plainTag;
    }

    /**
     * Localization key for this state's label (resolved via LanguageManager in the caller's language).
     * Prefer this over {@link #getDisplayTag()} on any per-recipient surface (alerts, /aiac check) so the
     * threat label is shown in the viewer's language rather than a single hardcoded one.
     */
    public String messageKey() {
        return switch (this) {
            case CLEAN -> "threat_state.clean";
            case SUSPICIOUS -> "threat_state.suspicious";
            case HIGH_CONFIDENCE -> "threat_state.high_confidence";
            case CONFIRMED -> "threat_state.confirmed";
        };
    }

    public String getHexColor() {
        return hexColor;
    }

    public float getMinConfidence() {
        return minConfidence;
    }

    public float getMaxConfidence() {
        return maxConfidence;
    }

    public static ThreatState fromConfidence(float confidence) {
        float clamped = Math.max(0.0f, Math.min(1.0f, confidence));
        if (clamped >= 0.90f) return CONFIRMED;
        if (clamped >= 0.70f) return HIGH_CONFIDENCE;
        if (clamped >= 0.40f) return SUSPICIOUS;
        return CLEAN;
    }
}
