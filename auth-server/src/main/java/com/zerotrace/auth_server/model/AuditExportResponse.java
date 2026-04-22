package com.zerotrace.auth_server.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

@Getter
@AllArgsConstructor
public class AuditExportResponse {
    private Long requestId;
    private String status;
    private String detail;
    private Instant expiresAt;
}
