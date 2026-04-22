package com.zerotrace.core.client;

import com.zerotrace.crypto.RSAUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;

public class KeyManager {

    private final Path baseDirectory;

    public KeyManager() {
        this(Path.of(System.getProperty("user.home"), ".zerotrace", "keys"));
    }

    public KeyManager(Path baseDirectory) {
        this.baseDirectory = baseDirectory;
    }

    public KeyPair loadOrCreate(String username) throws Exception {
        Path userDirectory = baseDirectory.resolve(username);
        Path privateKeyPath = userDirectory.resolve("private.key");
        Path publicKeyPath = userDirectory.resolve("public.key");

        if (Files.exists(privateKeyPath) && Files.exists(publicKeyPath)) {
            PrivateKey privateKey = RSAUtil.getPrivateKeyFromString(Files.readString(privateKeyPath).trim());
            PublicKey publicKey = RSAUtil.getPublicKeyFromString(Files.readString(publicKeyPath).trim());
            return new KeyPair(publicKey, privateKey);
        }

        Files.createDirectories(userDirectory);
        KeyPair keyPair = RSAUtil.generateKeyPair();
        Files.writeString(privateKeyPath, Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded()));
        Files.writeString(publicKeyPath, Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()));
        return keyPair;
    }

    public String getPublicKeyString(KeyPair keyPair) {
        return RSAUtil.getPublicKeyAsString(keyPair.getPublic());
    }

    public Path getBaseDirectory() {
        return baseDirectory;
    }
}
