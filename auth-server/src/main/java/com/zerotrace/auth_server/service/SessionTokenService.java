package com.zerotrace.auth_server.service;

import com.zerotrace.auth_server.model.SessionToken;
import com.zerotrace.auth_server.repository.SessionTokenRepository;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;

@Service
public class SessionTokenService {

    private static final int TOKEN_BYTES = 48;
    private static final long TOKEN_TTL_HOURS = 24;

    private final SessionTokenRepository sessionTokenRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public SessionTokenService(SessionTokenRepository sessionTokenRepository) {
        this.sessionTokenRepository = sessionTokenRepository;
    }

    public String issueToken(String username) {
        revokeAllForUser(username);

        byte[] buffer = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(buffer);

        SessionToken sessionToken = new SessionToken();
        sessionToken.setUsername(username);
        sessionToken.setToken(Base64.getUrlEncoder().withoutPadding().encodeToString(buffer));
        sessionToken.setExpiresAt(Instant.now().plus(TOKEN_TTL_HOURS, ChronoUnit.HOURS));

        return sessionTokenRepository.save(sessionToken).getToken();
    }

    public Optional<String> validate(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }

        return sessionTokenRepository.findByToken(token)
                .filter(found -> !found.isRevoked())
                .filter(found -> found.getExpiresAt().isAfter(Instant.now()))
                .map(SessionToken::getUsername);
    }

    private void revokeAllForUser(String username) {
        var activeTokens = sessionTokenRepository.findByUsernameAndRevokedFalse(username);
        activeTokens.forEach(token -> token.setRevoked(true));
        sessionTokenRepository.saveAll(activeTokens);
    }
}
