package com.zerotrace.core.client;

import com.zerotrace.core.EncryptionService;
import com.zerotrace.crypto.RSAUtil;
import com.zerotrace.model.MessagePacket;
import com.zerotrace.model.MessageRequest;
import com.zerotrace.model.SendMessageResponse;

import java.security.KeyPair;
import java.security.PublicKey;

public class AttackSimulator {

    public static void run(String[] args, String serverUrl) throws Exception {
        if (args.length == 0) {
            printUsage();
            return;
        }

        AuthClient authClient = new AuthClient(serverUrl);
        KeyManager keyManager = new KeyManager();

        switch (args[0]) {
            case "tamper" -> tamper(authClient, keyManager, args);
            case "bruteforce" -> bruteForce(authClient, keyManager, args);
            case "flood" -> flood(authClient, keyManager, args);
            default -> printUsage();
        }
    }

    private static void tamper(AuthClient authClient, KeyManager keyManager, String[] args) throws Exception {
        if (args.length < 4) {
            printUsage();
            return;
        }

        String sender = args[1];
        String password = args[2];
        String receiver = args[3];

        KeyPair senderKeys = keyManager.loadOrCreate(sender);
        authClient.login(sender, password, keyManager.getPublicKeyString(senderKeys));
        PublicKey receiverPublicKey = RSAUtil.getPublicKeyFromString(authClient.getPublicKey(receiver));

        MessagePacket packet = EncryptionService.encryptForRelay(
                sender,
                receiver,
                "tampered-test-payload",
                senderKeys,
                receiverPublicKey
        );

        String badSignature = packet.getSignature().substring(0, packet.getSignature().length() - 4) + "ABCD";
            authClient.sendMessage(new MessageRequest(
                    packet.getSender(),
                    packet.getReceiver(),
                    packet.getEncryptedMessage(),
                    packet.getEncryptedAESKey(),
                    badSignature,
                    packet.getIv(),
                    "PRIVATE",
                    60
            ));

        System.out.println("Injected tampered packet. Fetch the receiver inbox to confirm integrity rejection.");
    }

    private static void bruteForce(AuthClient authClient, KeyManager keyManager, String[] args) throws Exception {
        if (args.length < 3) {
            printUsage();
            return;
        }

        String username = args[1];
        String wrongPassword = args[2];
        KeyPair keys = keyManager.loadOrCreate(username);

        for (int i = 1; i <= 6; i++) {
            try {
                authClient.login(username, wrongPassword, keyManager.getPublicKeyString(keys));
                System.out.println("Unexpectedly logged in on attempt " + i);
            } catch (Exception exception) {
                System.out.println("Failed login attempt " + i + ": " + exception.getMessage());
            }
        }
    }

    private static void flood(AuthClient authClient, KeyManager keyManager, String[] args) throws Exception {
        if (args.length < 5) {
            printUsage();
            return;
        }

        String sender = args[1];
        String password = args[2];
        String receiver = args[3];
        int count = Integer.parseInt(args[4]);

        KeyPair senderKeys = keyManager.loadOrCreate(sender);
        authClient.login(sender, password, keyManager.getPublicKeyString(senderKeys));
        PublicKey receiverPublicKey = RSAUtil.getPublicKeyFromString(authClient.getPublicKey(receiver));

        for (int i = 0; i < count; i++) {
            String message = "flood-message-" + i + "-" + "X".repeat(256);
            MessagePacket packet = EncryptionService.encryptForRelay(
                    sender,
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
                    "PRIVATE",
                    60
            ));

            if (response.getThreatAnalysis() != null && !"NORMAL".equals(response.getThreatAnalysis().getVerdict())) {
                System.out.println("Attack detected on message " + i + ": "
                        + response.getThreatAnalysis().getDetail());
            }
        }

        System.out.println("Flood simulation complete");
    }

    private static void printUsage() {
        System.out.println("""
                Attack usage:
                  attack tamper <sender> <password> <receiver>
                  attack bruteforce <username> <wrongPassword>
                  attack flood <sender> <password> <receiver> <count>
                """);
    }
}
