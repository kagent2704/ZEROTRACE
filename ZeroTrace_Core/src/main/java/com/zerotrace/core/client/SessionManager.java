package com.zerotrace.core.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;

public class SessionManager {

    private final Path sessionFile;

    public SessionManager() {
        this(Path.of(System.getProperty("user.home"), ".zerotrace", "session.properties"));
    }

    public SessionManager(Path sessionFile) {
        this.sessionFile = sessionFile;
    }

    public void save(String username, String serverUrl, String token, String organization, String role, boolean auditModeApproved) throws IOException {
        Files.createDirectories(sessionFile.getParent());
        Properties properties = new Properties();
        properties.setProperty("username", username);
        properties.setProperty("serverUrl", serverUrl);
        properties.setProperty("token", token);
        properties.setProperty("organization", organization == null ? "" : organization);
        properties.setProperty("role", role == null ? "USER" : role);
        properties.setProperty("auditModeApproved", Boolean.toString(auditModeApproved));

        try (OutputStream outputStream = Files.newOutputStream(sessionFile)) {
            properties.store(outputStream, "ZeroTrace session");
        }
    }

    public Optional<Session> load() throws IOException {
        if (!Files.exists(sessionFile)) {
            return Optional.empty();
        }

        Properties properties = new Properties();
        try (InputStream inputStream = Files.newInputStream(sessionFile)) {
            properties.load(inputStream);
        }

        String username = properties.getProperty("username");
        String serverUrl = properties.getProperty("serverUrl");
        String token = properties.getProperty("token");
        String organization = properties.getProperty("organization", "");
        String role = properties.getProperty("role", "USER");
        boolean auditModeApproved = Boolean.parseBoolean(properties.getProperty("auditModeApproved", "false"));
        if (username == null || serverUrl == null || token == null) {
            return Optional.empty();
        }

        return Optional.of(new Session(username, serverUrl, token, organization, role, auditModeApproved));
    }

    public void clear() throws IOException {
        if (Files.exists(sessionFile)) {
            Files.delete(sessionFile);
        }
    }

    public record Session(String username, String serverUrl, String token, String organization, String role, boolean auditModeApproved) {
    }
}
