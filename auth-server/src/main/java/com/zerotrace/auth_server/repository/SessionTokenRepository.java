package com.zerotrace.auth_server.repository;

import com.zerotrace.auth_server.model.SessionToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SessionTokenRepository extends JpaRepository<SessionToken, Long> {
    Optional<SessionToken> findByToken(String token);

    List<SessionToken> findByUsernameAndRevokedFalse(String username);
}
