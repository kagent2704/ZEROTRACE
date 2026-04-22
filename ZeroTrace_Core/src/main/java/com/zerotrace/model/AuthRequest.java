package com.zerotrace.model;

public class AuthRequest {
    private final String username;
    private final String password;
    private final String publicKey;
    private final String organization;

    public AuthRequest(String username, String password, String publicKey, String organization) {
        this.username = username;
        this.password = password;
        this.publicKey = publicKey;
        this.organization = organization;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public String getOrganization() {
        return organization;
    }
}
