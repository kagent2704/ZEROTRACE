package com.zerotrace.core;

import com.zerotrace.crypto.AESUtil;
import com.zerotrace.crypto.HashUtil;
import com.zerotrace.crypto.RSAUtil;
import com.zerotrace.crypto.SignatureUtil;
import com.zerotrace.model.MessagePacket;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;

public class EncryptionService {

    public static MessagePacket encryptForRelay(
            String sender,
            String receiver,
            String message,
            KeyPair senderKeys,
            PublicKey receiverPublicKey
    ) throws Exception {
        SecretKey aesKey = AESUtil.generateKey();
        AESUtil.AESResult aesResult = AESUtil.encrypt(message, aesKey);

        byte[] encryptedAESKey = RSAUtil.encrypt(aesKey.getEncoded(), receiverPublicKey);
        byte[] hash = HashUtil.sha256(message);
        byte[] signature = SignatureUtil.sign(hash, senderKeys.getPrivate());

        return new MessagePacket(
                null,
                sender,
                receiver,
                aesResult.encryptedData,
                Base64.getEncoder().encodeToString(encryptedAESKey),
                Base64.getEncoder().encodeToString(signature),
                aesResult.iv,
                null,
                null,
                null
        );
    }

    public static String decryptFromRelay(
            MessagePacket packet,
            PrivateKey receiverPrivateKey,
            PublicKey senderPublicKey
    ) throws Exception {
        byte[] decryptedAESKeyBytes = RSAUtil.decrypt(
                Base64.getDecoder().decode(packet.getEncryptedAESKey()),
                receiverPrivateKey
        );

        SecretKey aesKey = new SecretKeySpec(decryptedAESKeyBytes, "AES");
        String decryptedMessage = AESUtil.decrypt(packet.getEncryptedMessage(), packet.getIv(), aesKey);

        byte[] newHash = HashUtil.sha256(decryptedMessage);
        boolean isValid = SignatureUtil.verify(
                newHash,
                Base64.getDecoder().decode(packet.getSignature()),
                senderPublicKey
        );

        if (!isValid) {
            throw new SecurityException("Message integrity compromised");
        }

        return decryptedMessage;
    }
}
