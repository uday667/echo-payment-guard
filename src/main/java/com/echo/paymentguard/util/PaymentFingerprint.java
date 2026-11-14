package com.echo.paymentguard.util;

import com.echo.paymentguard.dto.PaymentRequest;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

public final class PaymentFingerprint {
    private PaymentFingerprint() {
    }

    public static String of(PaymentRequest request) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, request.debtorAccount());
            update(digest, request.creditorAccount());
            update(digest, request.amount().stripTrailingZeros().toPlainString());
            update(digest, request.currency().toUpperCase(Locale.ROOT));
            update(digest, request.channel());
            update(digest, request.reference());
            update(digest, request.initiatedAt().toInstant().toString());
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static void update(MessageDigest digest, String value) {
        if (value == null) {
            digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(-1).array());
            return;
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }
}
