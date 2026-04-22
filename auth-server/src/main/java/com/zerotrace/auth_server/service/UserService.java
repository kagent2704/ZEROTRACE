package com.zerotrace.auth_server.service;

import com.zerotrace.auth_server.model.AuthRequest;
import com.zerotrace.auth_server.model.AuthResponse;
import com.zerotrace.auth_server.model.User;
import com.zerotrace.auth_server.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final SessionTokenService sessionTokenService;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public UserService(UserRepository userRepository, SessionTokenService sessionTokenService) {
        this.userRepository = userRepository;
        this.sessionTokenService = sessionTokenService;
    }

    public AuthResponse register(AuthRequest request, HttpServletRequest httpRequest) {
        validateAuthRequest(request);

        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            return new AuthResponse(false, "Username already exists", request.getUsername(), null, null, null, null, false);
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPasswordHash(encoder.encode(request.getPassword()));
        user.setPublicKey(request.getPublicKey());
        if (request.getOrganization() != null && !request.getOrganization().isBlank()) {
            user.setOrganization(request.getOrganization().trim());
        }
        if (!userRepository.existsByOrganization(user.getOrganization())) {
            user.setRole("ADMIN");
            user.setAuditModeApproved(true);
            user.setExportApproved(true);
        }
        user.setFailedLoginAttempts(0);
        user.setLastKnownIp(resolveClientIp(httpRequest));
        user.setLastLoginAt(Instant.now());

        User savedUser = userRepository.save(user);
        String token = sessionTokenService.issueToken(savedUser.getUsername());
        return new AuthResponse(
                true,
                "Registration successful",
                savedUser.getUsername(),
                savedUser.getOrganization(),
                savedUser.getPublicKey(),
                token,
                savedUser.getRole(),
                savedUser.isAuditModeApproved()
        );
    }

    public AuthResponse login(AuthRequest request, HttpServletRequest httpRequest) {
        validateAuthRequest(request);

        Optional<User> userOptional = userRepository.findByUsername(request.getUsername());
        if (userOptional.isEmpty()) {
            return new AuthResponse(false, "Invalid credentials", request.getUsername(), null, null, null, null, false);
        }

        User user = userOptional.get();
        if (!encoder.matches(request.getPassword(), user.getPasswordHash())) {
            user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);
            userRepository.save(user);
            return new AuthResponse(false, "Invalid credentials", request.getUsername(), null, null, null, null, false);
        }

        if (request.getPublicKey() != null && !request.getPublicKey().isBlank()) {
            user.setPublicKey(request.getPublicKey());
        }

        user.setFailedLoginAttempts(0);
        user.setLastKnownIp(resolveClientIp(httpRequest));
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        String token = sessionTokenService.issueToken(user.getUsername());
        return new AuthResponse(
                true,
                "Login successful",
                user.getUsername(),
                user.getOrganization(),
                user.getPublicKey(),
                token,
                user.getRole(),
                user.isAuditModeApproved()
        );
    }

    public Optional<String> getPublicKey(String requesterUsername, String username) {
        User requester = getUser(requesterUsername);
        return userRepository.findByUsername(username)
                .filter(user -> user.getOrganization().equals(requester.getOrganization()))
                .map(User::getPublicKey);
    }

    public void syncPublicKey(String requesterUsername, String username, String publicKey) {
        if (!requesterUsername.equals(username)) {
            throw new IllegalArgumentException("You can only update your own public key");
        }
        if (publicKey == null || publicKey.isBlank()) {
            throw new IllegalArgumentException("Public key is required");
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        user.setPublicKey(publicKey);
        userRepository.save(user);
    }

    public User getUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    public User save(User user) {
        return userRepository.save(user);
    }

    public void requireOrgAdmin(String username) {
        User user = getUser(username);
        if (!"ADMIN".equalsIgnoreCase(user.getRole())) {
            throw new IllegalArgumentException("Admin privileges required");
        }
    }

    public boolean isOrgAdmin(String username) {
        return "ADMIN".equalsIgnoreCase(getUser(username).getRole());
    }

    private void validateAuthRequest(AuthRequest request) {
        if (request.getUsername() == null || request.getUsername().isBlank()) {
            throw new IllegalArgumentException("Username is required");
        }
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new IllegalArgumentException("Password is required");
        }
        if (request.getPublicKey() == null || request.getPublicKey().isBlank()) {
            throw new IllegalArgumentException("Public key is required");
        }
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
