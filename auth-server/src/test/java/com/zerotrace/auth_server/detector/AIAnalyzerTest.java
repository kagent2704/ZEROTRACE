package com.zerotrace.auth_server.detector;

import com.zerotrace.auth_server.model.ThreatAnalysisResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AIAnalyzerTest {

    @Test
    void flagsHighRiskTrafficAsAnomaly() {
        ThreatAnalysisResult result = AIAnalyzer.analyze(new double[]{30, 0.8, 6000, 6, 4, 3});

        assertEquals("ANOMALY", result.getVerdict());
        assertTrue(result.getDetail().contains("DL risk"));
    }

    @Test
    void keepsNormalTrafficWithinBaseline() {
        ThreatAnalysisResult result = AIAnalyzer.analyze(new double[]{2, 25, 256, 1, 0, 0});

        assertEquals("NORMAL", result.getVerdict());
        assertTrue(result.getDetail().contains("within baseline"));
    }
}
