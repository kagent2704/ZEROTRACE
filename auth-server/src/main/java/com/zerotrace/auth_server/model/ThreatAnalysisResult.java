package com.zerotrace.auth_server.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ThreatAnalysisResult {
    private String verdict;
    private String detail;
}
