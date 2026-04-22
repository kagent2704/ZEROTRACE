package com.zerotrace;

import com.zerotrace.core.EncryptionService;
import com.zerotrace.core.client.AttackSimulator;
import com.zerotrace.core.client.AuthClient;
import com.zerotrace.core.client.KeyManager;
import com.zerotrace.core.client.SessionManager;
import com.zerotrace.crypto.RSAUtil;
import com.zerotrace.model.AuthResponse;
import com.zerotrace.model.MessagePacket;
import com.zerotrace.model.MessageRequest;
import com.zerotrace.model.SendMessageResponse;
import com.zerotrace.ui.ZeroTraceFinalApp;

import java.security.KeyPair;
import java.security.PublicKey;
import java.util.List;
import java.util.Optional;

public class Main {

    private static final String DEFAULT_SERVER_URL = System.getenv().getOrDefault("ZEROTRACE_SERVER_URL", "http://localhost:8080");

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            ZeroTraceFinalApp.launchApp(args);
            return;
        }

        KeyManager keyManager = new KeyManager();
        SessionManager sessionManager = new SessionManager();

        switch (args[0]) {
            case "register" -> register(args, keyManager, sessionManager);
            case "login" -> login(args, keyManager, sessionManager);
            case "send" -> send(args, keyManager, sessionManager);
            case "inbox" -> inbox(keyManager, sessionManager);
            case "approve-audit" -> approveAudit(args, sessionManager);
            case "request-export" -> requestExport(args, sessionManager);
            case "my-exports" -> myExports(sessionManager);
            case "pending-exports" -> pendingExports(sessionManager);
            case "approve-export" -> approveExport(args, sessionManager);
            case "export-audit" -> exportAudit(args, sessionManager);
            case "attack" -> AttackSimulator.run(slice(args, 1), DEFAULT_SERVER_URL);
            case "whoami" -> whoAmI(sessionManager);
            default -> printUsage();
        }
    }

    private static void register(String[] args, KeyManager keyManager, SessionManager sessionManager) throws Exception {
        if (args.length < 3) {
            printUsage();
            return;
        }

        String username = args[1];
        String password = args[2];
        String organization = args.length > 3 ? args[3] : "default-org";
        KeyPair keyPair = keyManager.loadOrCreate(username);
        AuthClient authClient = new AuthClient(DEFAULT_SERVER_URL);
        AuthResponse response = authClient.register(username, password, keyManager.getPublicKeyString(keyPair), organization);

        System.out.println(response.getMessage());
        if (response.isSuccess()) {
            sessionManager.save(
                    username,
                    DEFAULT_SERVER_URL,
                    response.getToken(),
                    response.getOrganization(),
                    response.getRole(),
                    response.isAuditModeApproved()
            );
            System.out.println("Stored local keys in: " + keyManager.getBaseDirectory().resolve(username));
        }
    }

    private static void login(String[] args, KeyManager keyManager, SessionManager sessionManager) throws Exception {
        if (args.length < 3) {
            printUsage();
            return;
        }

        String username = args[1];
        String password = args[2];
        KeyPair keyPair = keyManager.loadOrCreate(username);
        AuthClient authClient = new AuthClient(DEFAULT_SERVER_URL);
        AuthResponse response = authClient.login(username, password, keyManager.getPublicKeyString(keyPair));

        System.out.println(response.getMessage());
        if (response.isSuccess()) {
            sessionManager.save(
                    username,
                    DEFAULT_SERVER_URL,
                    response.getToken(),
                    response.getOrganization(),
                    response.getRole(),
                    response.isAuditModeApproved()
            );
        }
    }

    private static void send(String[] args, KeyManager keyManager, SessionManager sessionManager) throws Exception {
        if (args.length < 3) {
            printUsage();
            return;
        }

        SessionManager.Session session = requireSession(sessionManager);
        String receiver = args[1];
        String mode = args.length > 2 ? args[2].toUpperCase() : "PRIVATE";
        int ttlSeconds = args.length > 3 ? Integer.parseInt(args[3]) : 60;
        String message = String.join(" ", slice(args, 4));
        if (message.isBlank()) {
            throw new IllegalArgumentException("Message body is required");
        }

        KeyPair senderKeys = keyManager.loadOrCreate(session.username());
        AuthClient authClient = new AuthClient(session.serverUrl(), session.token());
        PublicKey receiverPublicKey = RSAUtil.getPublicKeyFromString(authClient.getPublicKey(receiver));
        MessagePacket packet = EncryptionService.encryptForRelay(
                session.username(),
                receiver,
                message,
                senderKeys,
                receiverPublicKey
        );

        SendMessageResponse response = authClient.sendMessage(new MessageRequest(
                packet.getSender(),
                packet.getReceiver(),
                packet.getEncryptedMessage(),
                packet.getEncryptedAESKey(),
                packet.getSignature(),
                packet.getIv(),
                mode,
                "AUDIT".equals(mode) ? null : ttlSeconds
        ));

        System.out.println("Message relayed with id " + response.getMessageId());
        if (response.getThreatAnalysis() != null) {
            System.out.println("Threat monitor: " + response.getThreatAnalysis().getVerdict()
                    + " - " + response.getThreatAnalysis().getDetail());
        }
    }

    private static void approveAudit(String[] args, SessionManager sessionManager) throws Exception {
        if (args.length < 3) {
            printUsage();
            return;
        }
        SessionManager.Session session = requireSession(sessionManager);
        new AuthClient(session.serverUrl(), session.token()).setAuditApproval(args[1], Boolean.parseBoolean(args[2]));
        System.out.println("Audit approval updated for " + args[1]);
    }

    private static void requestExport(String[] args, SessionManager sessionManager) throws Exception {
        SessionManager.Session session = requireSession(sessionManager);
        int lookbackDays = args.length > 1 ? Integer.parseInt(args[1]) : 7;
        var response = new AuthClient(session.serverUrl(), session.token()).createExportRequest(lookbackDays);
        System.out.println("Export request " + response.getRequestId() + ": " + response.getStatus() + " - " + response.getDetail());
    }

    private static void pendingExports(SessionManager sessionManager) throws Exception {
        SessionManager.Session session = requireSession(sessionManager);
        var requests = new AuthClient(session.serverUrl(), session.token()).pendingExportRequests();
        if (requests.isEmpty()) {
            System.out.println("No pending export requests");
            return;
        }
        for (var request : requests) {
            System.out.println(request.getRequestId() + ": " + request.getDetail());
        }
    }

    private static void myExports(SessionManager sessionManager) throws Exception {
        SessionManager.Session session = requireSession(sessionManager);
        var requests = new AuthClient(session.serverUrl(), session.token()).myExportRequests();
        if (requests.isEmpty()) {
            System.out.println("No export requests found");
            return;
        }
        for (var request : requests) {
            System.out.println(request.getRequestId() + ": " + request.getStatus() + " - " + request.getDetail() + " (expires " + request.getExpiresAt() + ")");
        }
    }

    private static void approveExport(String[] args, SessionManager sessionManager) throws Exception {
        if (args.length < 3) {
            printUsage();
            return;
        }
        SessionManager.Session session = requireSession(sessionManager);
        new AuthClient(session.serverUrl(), session.token()).approveExportRequest(Long.parseLong(args[1]), Boolean.parseBoolean(args[2]));
        System.out.println("Export request updated");
    }

    private static void exportAudit(String[] args, SessionManager sessionManager) throws Exception {
        if (args.length < 2) {
            printUsage();
            return;
        }
        SessionManager.Session session = requireSession(sessionManager);
        var export = new AuthClient(session.serverUrl(), session.token()).exportAuditData(Long.parseLong(args[1]));
        System.out.println(export);
    }

    private static void inbox(KeyManager keyManager, SessionManager sessionManager) throws Exception {
        SessionManager.Session session = requireSession(sessionManager);
        AuthClient authClient = new AuthClient(session.serverUrl(), session.token());
        KeyPair receiverKeys = keyManager.loadOrCreate(session.username());
        List<MessagePacket> messages = authClient.fetchInbox(session.username());

        if (messages.isEmpty()) {
            System.out.println("No new messages");
            return;
        }

        for (MessagePacket packet : messages) {
            PublicKey senderPublicKey = RSAUtil.getPublicKeyFromString(authClient.getPublicKey(packet.getSender()));
            try {
                String plaintext = EncryptionService.decryptFromRelay(packet, receiverKeys.getPrivate(), senderPublicKey);
                System.out.println("[" + packet.getCreatedAt() + "] " + packet.getSender() + ": " + plaintext);
            } catch (Exception securityException) {
                System.out.println("[" + packet.getCreatedAt() + "] " + packet.getSender()
                        + ": rejected tampered message (" + securityException.getMessage() + ")");
            }
        }
    }

    private static void whoAmI(SessionManager sessionManager) throws Exception {
        Optional<SessionManager.Session> session = sessionManager.load();
        if (session.isEmpty()) {
            System.out.println("No local session");
            return;
        }
        System.out.println("Logged in as " + session.get().username() + " (" + session.get().role() + ") against " + session.get().serverUrl());
    }

    private static SessionManager.Session requireSession(SessionManager sessionManager) throws Exception {
        return sessionManager.load()
                .orElseThrow(() -> new IllegalStateException("No local session. Run login first."));
    }

    private static String[] slice(String[] input, int startIndex) {
        String[] result = new String[Math.max(0, input.length - startIndex)];
        System.arraycopy(input, startIndex, result, 0, result.length);
        return result;
    }

    private static void printUsage() {
        System.out.println("""
                ZeroTrace CLI
                Usage:
                  register <username> <password> [organization]
                  login <username> <password>
                  send <receiver> <PRIVATE|AUDIT> <ttlSeconds|0> <message>
                  inbox
                  whoami
                  approve-audit <username> <true|false>
                  request-export [lookbackDays]
                  my-exports
                  pending-exports
                  approve-export <requestId> <true|false>
                  export-audit <requestId>
                  attack <tamper|bruteforce|flood> [args]

                Set ZEROTRACE_SERVER_URL to point to a remote server.
                """);
    }
}
