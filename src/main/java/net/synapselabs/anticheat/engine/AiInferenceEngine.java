package net.synapselabs.anticheat.engine;

import ai.onnxruntime.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.Map;
import java.util.logging.Level;

/**
 * High-performance, thread-safe ONNX Runtime inference engine.
 */
public class AiInferenceEngine implements AutoCloseable {
    private final JavaPlugin plugin;
    private OrtEnvironment env;
    private OrtSession session;
    private boolean initialized = false;

    public record PredictionResult(int predictedClass, float cheatProbability) {}

    public AiInferenceEngine(JavaPlugin plugin) {
        this.plugin = plugin;
        initEngine();
    }

    private void initEngine() {
        try {
            this.env = OrtEnvironment.getEnvironment();
            File modelFile = new File(plugin.getDataFolder(), "anticheat_model.onnx");

            if (!modelFile.exists()) {
                plugin.getDataFolder().mkdirs();
                try (InputStream in = plugin.getResource("anticheat_model.onnx")) {
                    if (in != null) {
                        Files.copy(in, modelFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    } else {
                        plugin.getLogger().severe("Embedded anticheat_model.onnx resource not found!");
                        return;
                    }
                }
            }

            OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
            opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
            opts.setIntraOpNumThreads(2);

            this.session = env.createSession(modelFile.getAbsolutePath(), opts);
            this.initialized = true;
            plugin.getLogger().info("Synapse AI ONNX Model successfully loaded into RAM (<0.3ms latency)!");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize ONNX Runtime Engine", e);
        }
    }

    public boolean isInitialized() {
        return initialized;
    }

    public synchronized PredictionResult predict(float[] features) {
        if (!initialized || session == null) {
            return new PredictionResult(0, 0.0f);
        }

        if (features == null || features.length != FeatureSchema.FEATURE_COUNT) {
            String len = (features == null) ? "null" : String.valueOf(features.length);
            plugin.getLogger().severe("[AI Inference Schema Mismatch] Expected " + FeatureSchema.FEATURE_COUNT + " features, got " + len);
            throw new IllegalArgumentException("Feature count mismatch: expected " + FeatureSchema.FEATURE_COUNT + ", got " + len);
        }

        try {
            float[][] inputData = new float[][]{features};
            OnnxTensor inputTensor = OnnxTensor.createTensor(env, inputData);

            String inputName = session.getInputNames().iterator().next();
            try (OrtSession.Result results = session.run(Collections.singletonMap(inputName, inputTensor))) {
                float cheatProb = 0.0f;
                int predictedLabel = 0;

                Object firstOut = results.get(0).getValue();
                if (firstOut instanceof float[][] fArr && fArr.length > 0) {
                    if (fArr[0].length == 1) {
                        // PyTorch Sigmoid output [1, 1]
                        cheatProb = fArr[0][0];
                        predictedLabel = cheatProb >= 0.5f ? 1 : 0;
                    } else if (fArr[0].length >= 2) {
                        cheatProb = fArr[0][1];
                        predictedLabel = cheatProb >= 0.5f ? 1 : 0;
                    }
                } else if (firstOut instanceof long[] labels && labels.length > 0) {
                    predictedLabel = (int) labels[0];
                    cheatProb = predictedLabel == 1 ? 0.95f : 0.05f;

                    if (results.size() > 1) {
                        Object probObj = results.get(1).getValue();
                        if (probObj instanceof java.util.List<?> list && !list.isEmpty()) {
                            Object first = list.get(0);
                            if (first instanceof Map<?, ?> map) {
                                for (Map.Entry<?, ?> entry : map.entrySet()) {
                                    if (entry.getKey().toString().equals("1") && entry.getValue() instanceof Number n) {
                                        cheatProb = n.floatValue();
                                    }
                                }
                            }
                        } else if (probObj instanceof float[][] probArr && probArr.length > 0 && probArr[0].length > 1) {
                            cheatProb = probArr[0][1];
                        }
                    }
                }

                inputTensor.close();
                return new PredictionResult(predictedLabel, cheatProb);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error during AI model inference", e);
            return new PredictionResult(0, 0.0f);
        }
    }

    @Override
    public void close() {
        try {
            if (session != null) session.close();
            if (env != null) env.close();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error closing ONNX environment", e);
        }
    }
}
