package com.clothingplatform.payment;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class RazorpaySignatureTest {
    // expected values computed independently with Python's hmac/hashlib
    private static final String PAYMENT_SIG = "15656b40fea6f2159b578efa459e969de9f5e223fb8a08393e274ac578d9d005";
    private static final String WEBHOOK_SIG = "4673dd707ef4c41b987cb7fefe1583142dc702388c93145b7814b9ad3d3c183e";

    @Test
    void acceptsTheCorrectCheckoutSignature() {
        assertTrue(RazorpaySignature.verifyPayment("order_ABC", "pay_XYZ", PAYMENT_SIG, "test_secret"));
    }

    @Test
    void rejectsTamperedOrderPaymentSecretOrMissingSignature() {
        assertFalse(RazorpaySignature.verifyPayment("order_ABD", "pay_XYZ", PAYMENT_SIG, "test_secret"));
        assertFalse(RazorpaySignature.verifyPayment("order_ABC", "pay_XYW", PAYMENT_SIG, "test_secret"));
        assertFalse(RazorpaySignature.verifyPayment("order_ABC", "pay_XYZ", PAYMENT_SIG, "other_secret"));
        assertFalse(RazorpaySignature.verifyPayment("order_ABC", "pay_XYZ", null, "test_secret"));
    }

    @Test
    void verifiesWebhookBodyExactly() {
        assertTrue(RazorpaySignature.verifyWebhook("{\"event\":\"payment.captured\"}", WEBHOOK_SIG, "whsec"));
        assertFalse(RazorpaySignature.verifyWebhook("{\"event\": \"payment.captured\"}", WEBHOOK_SIG, "whsec"));
    }
}
