package com.zerotrace.model;

public class AuditExportResponse {
    private Long requestId;
    private String status;
    private String detail;
    private String expiresAt;

    public Long getRequestId() {
        return requestId;
    }

    public String getStatus() {
        return status;
    }

    public String getDetail() {
        return detail;
    }

    public String getExpiresAt() {
        return expiresAt;
    }
}
