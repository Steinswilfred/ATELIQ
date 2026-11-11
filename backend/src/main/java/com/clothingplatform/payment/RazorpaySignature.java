package com.clothingplatform.payment;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** HMAC-SHA256 checks required by Razorpay. All comparisons are constant-time. */
public final class RazorpaySignature {
    private RazorpaySignature() {}

    public static String hmacHex(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Checkout callback: signature = HMAC(order_id + "|" + payment_id) with the key secret. */
    public static boolean verifyPayment(String razorpayOrderId, String razorpayPaymentId, String signature, String keySecret) {
        return safeEquals(hmacHex(keySecret, razorpayOrderId + "|" + razorpayPaymentId), signature);
    }

    /** Webhook: signature = HMAC(raw request body) with the webhook secret. Never re-serialise the body before checking. */
    public static boolean verifyWebhook(String rawBody, String signature, String webhookSecret) {
        return safeEquals(hmacHex(webhookSecret, rawBody), signature);
    }

    private static boolean safeEquals(String expected, String given) {
        if (given == null) return false;
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), given.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
    }
}
