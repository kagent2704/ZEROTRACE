package com.zerotrace.auth_server.crypto;

import java.security.MessageDigest;

public class HashUtil {

    public static byte[] sha256(String data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return digest.digest(data.getBytes());
    }
}