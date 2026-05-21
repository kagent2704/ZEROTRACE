package com.zerotrace.auth_server.detector;

import com.zerotrace.auth_server.model.ThreatAnalysisResult;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

public final class AIAnalyzer {

    private static final String SCRIPT_RESOURCE = "/models/predict.py";
    private static final String SPREAD_MODEL_RESOURCE = "/models/anomaly_model.pkl";
    private static final String PAYLOAD_MODEL_RESOURCE = "/models/payload_model.pkl";
    private static final PythonResources PYTHON_RESOURCES = prepareResources();

    private AIAnalyzer() {
    }

    public static ThreatAnalysisResult analyze(double[] features) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                    resolvePythonCommand(),
                    PYTHON_RESOURCES.scriptPath().toString(),
                    String.valueOf(features[0]),
                    String.valueOf(features[1]),
                    String.valueOf(features[2]),
                    String.valueOf(features[3]),
                    String.valueOf(features[4]),
                    String.valueOf(features[5])
            );
            processBuilder.directory(PYTHON_RESOURCES.scriptPath().getParent().toFile());
            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();
            String output;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                output = reader.readLine();
            }

            int exitCode = process.waitFor();
            if (exitCode == 0 && output != null && !output.isBlank()) {
                return parsePrediction(output.trim());
            }
        } catch (Exception ignored) {
        }

        return new ThreatAnalysisResult("NORMAL", "AI detector unavailable");
    }

    private static ThreatAnalysisResult parsePrediction(String output) {
        String[] parts = output.split("\\|", 3);
        if (parts.length < 3) {
            return new ThreatAnalysisResult("NORMAL", "AI detector unavailable");
        }

        String verdict = parts[0].trim().toUpperCase(Locale.ROOT);
        double score;
        try {
            score = Double.parseDouble(parts[1].trim());
        } catch (NumberFormatException exception) {
            return new ThreatAnalysisResult("NORMAL", "AI detector unavailable");
        }
        String label = parts[2].trim();

        if (!"ANOMALY".equals(verdict)) {
            return new ThreatAnalysisResult(
                    "NORMAL",
                    String.format(Locale.ROOT, "Statistical score %.4f - Network behaviour normal", score)
            );
        }

        return new ThreatAnalysisResult(
                "ANOMALY",
                String.format(Locale.ROOT, "Statistical score %.4f - %s", score, label)
        );
    }

    private static PythonResources prepareResources() {
        try {
            Path resourceDir = Files.createTempDirectory("zerotrace-threat-model");
            resourceDir.toFile().deleteOnExit();
            Path scriptPath = resourceDir.resolve("predict.py");
            Path spreadModelPath = resourceDir.resolve("anomaly_model.pkl");
            Path payloadModelPath = resourceDir.resolve("payload_model.pkl");
            copyResource(SCRIPT_RESOURCE, scriptPath);
            copyResource(SPREAD_MODEL_RESOURCE, spreadModelPath);
            copyResource(PAYLOAD_MODEL_RESOURCE, payloadModelPath);
            return new PythonResources(scriptPath, spreadModelPath, payloadModelPath);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to prepare AI model resources", exception);
        }
    }

    private static void copyResource(String resourceName, Path destination) throws Exception {
        try (InputStream inputStream = AIAnalyzer.class.getResourceAsStream(resourceName)) {
            if (inputStream == null) {
                throw new IllegalStateException("Missing resource " + resourceName);
            }
            Files.copy(inputStream, destination, StandardCopyOption.REPLACE_EXISTING);
            destination.toFile().deleteOnExit();
        }
    }

    private static String resolvePythonCommand() {
        String configured = System.getenv("ZEROTRACE_PYTHON");
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        return "python";
    }

    private record PythonResources(Path scriptPath, Path spreadModelPath, Path payloadModelPath) {
    }
}
