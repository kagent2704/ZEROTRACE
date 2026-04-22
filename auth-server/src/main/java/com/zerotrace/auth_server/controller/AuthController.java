package com.zerotrace.auth_server.controller;

import com.zerotrace.auth_server.model.AuthRequest;
import com.zerotrace.auth_server.model.AuthResponse;
import com.zerotrace.auth_server.model.PublicKeySyncRequest;
import com.zerotrace.auth_server.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody AuthRequest request, HttpServletRequest httpRequest) {
        AuthResponse response = userService.register(request, httpRequest);
        if (!response.isSuccess()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody AuthRequest request, HttpServletRequest httpRequest) {
        AuthResponse response = userService.login(request, httpRequest);
        if (!response.isSuccess()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
        return ResponseEntity.ok(response);
    }

    @PutMapping("/publicKey/{username}")
    public ResponseEntity<String> syncPublicKey(
            Authentication authentication,
            @PathVariable String username,
            @RequestBody PublicKeySyncRequest request
    ) {
        userService.syncPublicKey(authentication.getName(), username, request.getPublicKey());
        return ResponseEntity.ok("Public key updated");
    }

    @GetMapping("/publicKey/{username}")
    public ResponseEntity<?> getPublicKey(Authentication authentication, @PathVariable String username) {
        return userService.getPublicKey(authentication.getName(), username)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(404).body("User not found"));
    }
}
