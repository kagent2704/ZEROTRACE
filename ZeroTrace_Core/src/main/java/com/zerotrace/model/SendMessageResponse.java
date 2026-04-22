package com.zerotrace.model;

public class SendMessageResponse {
    private Long messageId;
    private String mode;
    private ThreatAnalysisResult threatAnalysis;

    public Long getMessageId() {
        return messageId;
    }

    public ThreatAnalysisResult getThreatAnalysis() {
        return threatAnalysis;
    }

    public String getMode() {
        return mode;
    }
}
