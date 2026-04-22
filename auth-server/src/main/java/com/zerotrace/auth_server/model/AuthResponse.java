package com.zerotrace.auth_server.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AuthResponse {
    private boolean success;
    private String message;
    private String username;
    private String organization;
    private String publicKey;
    private String token;
    private String role;
    private boolean auditModeApproved;
}
