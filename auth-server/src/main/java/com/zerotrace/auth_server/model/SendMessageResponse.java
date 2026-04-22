package com.zerotrace.auth_server.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SendMessageResponse {
    private Long messageId;
    private MessageMode mode;
    private ThreatAnalysisResult threatAnalysis;
}
