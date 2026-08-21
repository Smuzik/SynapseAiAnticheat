package net.synapselabs.anticheat.engine;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates that the embedded ONNX model artifact expects EXACTLY 24 inputs
 * and that the runtime inference vector can be executed without dimension mismatch.
 */
class OnnxModelParityTest {

    @Test
    void onnxModelInputDimensionMatchesFeatureCount() throws Exception {
        InputStream stream = getClass().getClassLoader().getResourceAsStream("anticheat_model.onnx");
        assertNotNull(stream, "anticheat_model.onnx must exist in plugin resources");

        Path tempFile = Files.createTempFile("onnx_test", ".onnx");
        tempFile.toFile().deleteOnExit();
        Files.copy(stream, tempFile, StandardCopyOption.REPLACE_EXISTING);

        try (OrtEnvironment env = OrtEnvironment.getEnvironment();
             OrtSession session = env.createSession(tempFile.toAbsolutePath().toString())) {

            assertEquals(1, session.getNumInputs(), "ONNX model must have exactly 1 input tensor");
            String inputName = session.getInputNames().iterator().next();
            assertTrue(inputName.equals("input") || inputName.equals("float_input"), "ONNX input tensor name must be 'input' or 'float_input'");

            // Execute test inference on neutral canonical 24-feature vector
            float[] neutral = new float[FeatureSchema.FEATURE_COUNT];
            for (int i = 0; i < FeatureSchema.FEATURE_COUNT; i++) {
                neutral[i] = FeatureSchema.neutralDefault(i);
            }
            assertEquals(24, neutral.length, "FeatureSchema.FEATURE_COUNT must be 24");

            float[][] batch = new float[][]{ neutral };
            try (ai.onnxruntime.OnnxTensor tensor = ai.onnxruntime.OnnxTensor.createTensor(env, batch);
                 OrtSession.Result result = session.run(Collections.singletonMap(inputName, tensor))) {
                assertNotNull(result, "Inference result must not be null");
                assertTrue(result.size() >= 1, "Inference result must contain predictions");
            }
        }
    }
}
