package com.zerotrace.auth_server.repository;

import com.zerotrace.auth_server.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);

    boolean existsByOrganization(String organization);
}
