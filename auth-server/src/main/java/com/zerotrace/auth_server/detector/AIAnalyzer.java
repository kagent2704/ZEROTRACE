package com.zerotrace.auth_server.detector;

import com.zerotrace.auth_server.model.ThreatAnalysisResult;

public class AIAnalyzer {

    public static ThreatAnalysisResult analyze(double[] features) {
        if (features[0] > 20 && features[1] < 3) {
            return new ThreatAnalysisResult("ANOMALY", "Possible spam bot activity");
        }
        if (features[4] > 5) {
            return new ThreatAnalysisResult("ANOMALY", "Brute force authentication attempt");
        }
        if (features[5] > 3) {
            return new ThreatAnalysisResult("ANOMALY", "Suspicious IP switching detected");
        }
        if (features[2] > 2_000) {
            return new ThreatAnalysisResult("ANOMALY", "Abnormally large message payload");
        }
        if (features[3] > 5) {
            return new ThreatAnalysisResult("ANOMALY", "Unusual peer connection pattern");
        }

        return new ThreatAnalysisResult("NORMAL", "Network behaviour normal");
    }
}
