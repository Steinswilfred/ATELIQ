package com.clothingplatform.payment;

import java.util.Map;

/** Provider abstraction. Only the Razorpay implementation exists today; the payment rules live in PaymentService, not here. */
public interface PaymentGateway {
    record GatewayOrder(String id, long amountPaise, String currency) {}
    record GatewayPayment(String id, String orderId, String status, long amountPaise, String method, String errorDescription) {}

    record GatewayRefund(String id, String status, long amountPaise) {}

    boolean isConfigured();
    String keyId();
    GatewayOrder createOrder(long amountPaise, String currency, String receipt, Map<String, String> notes);
    GatewayPayment fetchPayment(String paymentId);
    void capture(String paymentId, long amountPaise, String currency);
    /** status is Razorpay's: "processed" (done) or "pending" (a webhook will confirm) */
    GatewayRefund refund(String paymentId, long amountPaise, String receipt);
}
