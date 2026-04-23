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
    private static final String MODEL_RESOURCE = "/models/anomaly_model.pkl";
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
                return parsePrediction(output.trim(), features);
            }
        } catch (Exception ignored) {
        }

        return heuristicFallback(features);
    }

    private static ThreatAnalysisResult parsePrediction(String output, double[] features) {
        String[] parts = output.split("\\|", 3);
        if (parts.length < 3) {
            return heuristicFallback(features);
        }

        String verdict = parts[0].trim().toUpperCase(Locale.ROOT);
        double score;
        try {
            score = Double.parseDouble(parts[1].trim());
        } catch (NumberFormatException exception) {
            score = heuristicScore(features);
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

    private static ThreatAnalysisResult heuristicFallback(double[] features) {
        double score = heuristicScore(features);
        String label = triggerLabel(features);
        if (!"Network behaviour normal".equals(label)) {
            return new ThreatAnalysisResult(
                    "ANOMALY",
                    String.format(Locale.ROOT, "Statistical score %.4f - %s", score, label)
            );
        }
        return new ThreatAnalysisResult(
                "NORMAL",
                String.format(Locale.ROOT, "Statistical score %.4f - Network behaviour normal", score)
        );
    }

    private static double heuristicScore(double[] features) {
        double score = 0.0;
        score += Math.min(1.0, features[0] / 50.0) * 0.25;
        score += Math.min(1.0, Math.max(0.0, 1.0 - (features[1] / 5.0))) * 0.15;
        score += Math.min(1.0, features[2] / 2000.0) * 0.15;
        score += Math.min(1.0, features[3] / 5.0) * 0.15;
        score += Math.min(1.0, features[4] / 5.0) * 0.15;
        score += Math.min(1.0, features[5] / 3.0) * 0.15;
        return Math.max(0.0, Math.min(1.0, score));
    }

    private static String triggerLabel(double[] features) {
        double msgCount = features[0];
        double avgGap = features[1];
        double msgSize = features[2];
        double connections = features[3];
        double failedAttempts = features[4];
        double ipChanges = features[5];

        if (msgCount > 50 && avgGap < 1.0) {
            return "Possible Spam Bot Activity";
        }
        if (failedAttempts > 5) {
            return "Brute Force Authentication Attempt";
        }
        if (ipChanges > 3) {
            return "Suspicious IP Switching Detected";
        }
        if (msgSize > 2000) {
            return "Abnormally Large Message Payload";
        }
        if (connections > 5) {
            return "Unusual Peer Connection Pattern";
        }
        return "Network behaviour normal";
    }

    private static PythonResources prepareResources() {
        try {
            Path resourceDir = Files.createTempDirectory("zerotrace-threat-model");
            resourceDir.toFile().deleteOnExit();
            Path scriptPath = resourceDir.resolve("predict.py");
            Path modelPath = resourceDir.resolve("anomaly_model.pkl");
            copyResource(SCRIPT_RESOURCE, scriptPath);
            copyResource(MODEL_RESOURCE, modelPath);
            return new PythonResources(scriptPath, modelPath);
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

    private record PythonResources(Path scriptPath, Path modelPath) {
    }
}
