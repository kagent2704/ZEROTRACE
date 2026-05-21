package com.zerotrace.auth_server.detector;

import com.zerotrace.auth_server.model.ThreatAnalysisResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AIAnalyzerTest {

    @Test
    void flagsMassTargetSpreadAsAnomaly() {
        ThreatAnalysisResult result = AIAnalyzer.analyze(new double[]{30, 1.2, 320, 12, 0, 0});

        assertEquals("ANOMALY", result.getVerdict());
        assertTrue(result.getDetail().contains("Statistical score"));
        assertTrue(result.getDetail().contains("mass-target spread"));
        assertTrue(!result.getDetail().contains("abnormal payload size"));
    }

    @Test
    void flagsAbnormalPayloadSizeAsAnomaly() {
        ThreatAnalysisResult result = AIAnalyzer.analyze(new double[]{4, 40, 5000, 1, 0, 0});

        assertEquals("ANOMALY", result.getVerdict());
        assertTrue(result.getDetail().contains("Statistical score"));
        assertTrue(result.getDetail().contains("abnormal payload size"));
        assertTrue(!result.getDetail().contains("mass-target spread"));
    }

    @Test
    void keepsNormalTrafficWithinBaseline() {
        ThreatAnalysisResult result = AIAnalyzer.analyze(new double[]{8, 45, 256, 1, 0, 0});

        assertEquals("NORMAL", result.getVerdict());
        assertTrue(result.getDetail().contains("Network behaviour normal"));
    }
}
