package com.zerotrace.model;

public class AuthResponse {
    private boolean success;
    private String message;
    private String username;
    private String organization;
    private String publicKey;
    private String token;
    private String role;
    private boolean auditModeApproved;

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public String getUsername() {
        return username;
    }

    public String getOrganization() {
        return organization;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public String getToken() {
        return token;
    }

    public String getRole() {
        return role;
    }

    public boolean isAuditModeApproved() {
        return auditModeApproved;
    }
}
