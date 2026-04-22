package com.zerotrace.auth_server.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AuthRequest {
    private String username;
    private String password;
    private String publicKey;
    private String organization;
}
