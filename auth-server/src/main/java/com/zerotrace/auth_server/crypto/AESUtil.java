package com.zerotrace.auth_server.crypto;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import java.security.SecureRandom;
import java.util.Base64;

public class AESUtil {

    public static SecretKey generateKey() throws Exception {
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(256);
        return generator.generateKey();
    }

    public static class AESResult {
        public String encryptedData;
        public String iv;

        public AESResult(String encryptedData, String iv) {
            this.encryptedData = encryptedData;
            this.iv = iv;
        }
    }

    public static AESResult encrypt(String message, SecretKey key) throws Exception {

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");

        byte[] ivBytes = new byte[16];
        SecureRandom random = new SecureRandom();
        random.nextBytes(ivBytes);

        IvParameterSpec iv = new IvParameterSpec(ivBytes);

        cipher.init(Cipher.ENCRYPT_MODE, key, iv);

        byte[] encrypted = cipher.doFinal(message.getBytes());

        return new AESResult(
                Base64.getEncoder().encodeToString(encrypted),
                Base64.getEncoder().encodeToString(ivBytes)
        );
    }

    public static String decrypt(String encryptedData, String ivString, SecretKey key) throws Exception {

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");

        byte[] ivBytes = Base64.getDecoder().decode(ivString);
        IvParameterSpec iv = new IvParameterSpec(ivBytes);

        cipher.init(Cipher.DECRYPT_MODE, key, iv);

        byte[] decoded = Base64.getDecoder().decode(encryptedData);

        return new String(cipher.doFinal(decoded));
    }
}