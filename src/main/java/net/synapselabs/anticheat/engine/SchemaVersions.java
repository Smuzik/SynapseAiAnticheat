package net.synapselabs.anticheat.engine;

/**
 * Cross-cutting version tags for the anti-cheat data/model pipeline.
 *
 * <p>These identifiers are stamped onto detections (and, on the collector side, onto every dataset
 * row) so that any stored decision can be traced back to the exact feature layout, model export, and
 * plugin build that produced it. This is what makes the pipeline auditable and prevents silent
 * train/serve drift.
 *
 * <p>Keep these in sync with the collector's {@code python/feature_schema.py}
 * ({@code DATASET_VERSION}, {@code MODEL_VERSION}) and the plugin version in {@code plugin.yml}.
 */
public final class SchemaVersions {

    /** Feature layout version — delegates to the single source of truth. */
    public static final String FEATURE_SCHEMA_VERSION = FeatureSchema.VERSION;

    /**
     * Identifier of the currently deployed ONNX model export.
     * <p>Overridable at runtime via {@code detection.model.version} in config.yml so the model can be
     * swapped without a code change; this constant is the fallback default.
     */
    public static final String MODEL_VERSION_DEFAULT = "rf.calibrated.v2";

    /** Dataset row-format version shared with the collector. Bump when the CSV columns change. */
    public static final String DATASET_VERSION = "3.0";

    private SchemaVersions() {}
}
