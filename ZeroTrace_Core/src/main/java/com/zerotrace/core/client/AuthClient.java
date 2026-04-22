package com.zerotrace.core.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zerotrace.model.AuthRequest;
import com.zerotrace.model.AuthResponse;
import com.zerotrace.model.AuditExportResponse;
import com.zerotrace.model.MessagePacket;
import com.zerotrace.model.MessageRequest;
import com.zerotrace.model.SendMessageResponse;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public class AuthClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String serverUrl;
    private String authToken;

    public AuthClient(String serverUrl) {
        this(serverUrl, null);
    }

    public AuthClient(String serverUrl, String authToken) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
        this.serverUrl = stripTrailingSlash(serverUrl);
        this.authToken = authToken;
    }

    public AuthResponse register(String username, String password, String publicKey) throws IOException, InterruptedException {
        return register(username, password, publicKey, "default-org");
    }

    public AuthResponse register(String username, String password, String publicKey, String organization) throws IOException, InterruptedException {
        AuthResponse response = post("/auth/register", new AuthRequest(username, password, publicKey, organization), AuthResponse.class);
        if (response.isSuccess()) {
            this.authToken = response.getToken();
        }
        return response;
    }

    public AuthResponse login(String username, String password, String publicKey) throws IOException, InterruptedException {
        AuthResponse response = post("/auth/login", new AuthRequest(username, password, publicKey, null), AuthResponse.class);
        if (response.isSuccess()) {
            this.authToken = response.getToken();
        }
        return response;
    }

    public String getPublicKey(String username) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri("/auth/publicKey/" + encode(username))).GET();
        authorize(builder);
        HttpResponse<String> response = send(builder.build());
        return response.body();
    }

    public void syncPublicKey(String username, String publicKey) throws IOException, InterruptedException {
        post("/auth/publicKey/" + encode(username), Map.of("publicKey", publicKey), String.class, "PUT");
    }

    public SendMessageResponse sendMessage(MessageRequest request) throws IOException, InterruptedException {
        return post("/message/send", request, SendMessageResponse.class);
    }

    public List<MessagePacket> fetchInbox(String username) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri("/message/inbox/" + encode(username))).GET();
        authorize(builder);
        HttpResponse<String> response = send(builder.build());
        return objectMapper.readValue(response.body(), new TypeReference<>() {});
    }

    public AuditExportResponse createExportRequest(int lookbackDays) throws IOException, InterruptedException {
        return post("/governance/export-requests", Map.of("lookbackDays", lookbackDays), AuditExportResponse.class);
    }

    public List<AuditExportResponse> pendingExportRequests() throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri("/governance/export-requests/pending")).GET();
        authorize(builder);
        HttpResponse<String> response = send(builder.build());
        return objectMapper.readValue(response.body(), new TypeReference<>() {});
    }

    public List<AuditExportResponse> myExportRequests() throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri("/governance/export-requests/mine")).GET();
        authorize(builder);
        HttpResponse<String> response = send(builder.build());
        return objectMapper.readValue(response.body(), new TypeReference<>() {});
    }

    public void approveExportRequest(long requestId, boolean approved) throws IOException, InterruptedException {
        post("/governance/export-requests/" + requestId, Map.of("approved", approved), String.class, "PUT");
    }

    public void setAuditApproval(String username, boolean approved) throws IOException, InterruptedException {
        post("/governance/audit-approval/" + encode(username), Map.of("approved", approved), String.class, "PUT");
    }

    public Map<String, Object> exportAuditData(long requestId) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri("/governance/export/" + requestId)).GET();
        authorize(builder);
        HttpResponse<String> response = send(builder.build());
        return objectMapper.readValue(response.body(), new TypeReference<>() {});
    }

    public void writeJson(Path path, Object value) throws IOException {
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), value);
    }

    private <T> T post(String path, Object body, Class<T> responseType) throws IOException, InterruptedException {
        return post(path, body, responseType, "POST");
    }

    private <T> T post(String path, Object body, Class<T> responseType, String method) throws IOException, InterruptedException {
        String payload = objectMapper.writeValueAsString(body);
        HttpRequest.Builder request = HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(payload));
        authorize(request);

        HttpResponse<String> response = send(request.build());
        if (responseType == String.class) {
            return responseType.cast(response.body());
        }
        return objectMapper.readValue(response.body(), responseType);
    }

    private HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IOException("Server error (" + response.statusCode() + "): " + response.body());
        }
        return response;
    }

    private URI uri(String path) {
        return URI.create(serverUrl + path);
    }

    private void authorize(HttpRequest.Builder builder) {
        if (authToken != null && !authToken.isBlank()) {
            builder.header("Authorization", "Bearer " + authToken);
        }
    }

    private String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public String getAuthToken() {
        return authToken;
    }
}
