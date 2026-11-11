package com.clothingplatform.payment;

import com.clothingplatform.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Razorpay over its REST API (Basic auth with key id and secret from the environment). The secret never leaves the server.
 * Amounts are in paise. Error bodies are logged without credentials and never shown to customers.
 */
@Component
public class RazorpayGateway implements PaymentGateway {
    private static final Logger log = LoggerFactory.getLogger(RazorpayGateway.class);
    private final String keyId;
    private final String keySecret;
    private final RestClient http;

    public RazorpayGateway(@Value("${app.razorpay.key-id}") String keyId, @Value("${app.razorpay.key-secret}") String keySecret) {
        this.keyId = keyId; this.keySecret = keySecret;
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(8000); f.setReadTimeout(15000);
        this.http = RestClient.builder().baseUrl("https://api.razorpay.com/v1").requestFactory(f).defaultHeaders(h -> { if (!keyId.isBlank()) h.setBasicAuth(keyId, keySecret); }).build();
    }

    @Override public boolean isConfigured() { return !keyId.isBlank() && !keySecret.isBlank(); }
    @Override public String keyId() { return keyId; }

    @Override
    public GatewayOrder createOrder(long amountPaise, String currency, String receipt, Map<String, String> notes) {
        JsonNode r = call(() -> http.post().uri("/orders").body(Map.of("amount", amountPaise, "currency", currency, "receipt", receipt, "notes", notes)).retrieve().body(JsonNode.class));
        return new GatewayOrder(r.path("id").asText(), r.path("amount").asLong(), r.path("currency").asText());
    }

    @Override
    public GatewayPayment fetchPayment(String paymentId) {
        JsonNode r = call(() -> http.get().uri("/payments/{id}", paymentId).retrieve().body(JsonNode.class));
        return new GatewayPayment(r.path("id").asText(), r.path("order_id").asText(null), r.path("status").asText(), r.path("amount").asLong(), r.path("method").asText(null), r.path("error_description").asText(null));
    }

    @Override
    public void capture(String paymentId, long amountPaise, String currency) {
        call(() -> http.post().uri("/payments/{id}/capture", paymentId).body(Map.of("amount", amountPaise, "currency", currency)).retrieve().body(JsonNode.class));
    }

    @Override
    public GatewayRefund refund(String paymentId, long amountPaise, String receipt) {
        JsonNode r = call(() -> http.post().uri("/payments/{id}/refund", paymentId).body(Map.of("amount", amountPaise, "receipt", receipt)).retrieve().body(JsonNode.class));
        return new GatewayRefund(r.path("id").asText(), r.path("status").asText("pending"), r.path("amount").asLong(amountPaise));
    }

    private JsonNode call(java.util.function.Supplier<JsonNode> request) {
        try {
            JsonNode r = request.get();
            if (r == null) throw unavailable();
            return r;
        } catch (RestClientResponseException e) {
            log.error("Razorpay returned {}: {}", e.getStatusCode().value(), truncate(e.getResponseBodyAsString()));
            throw unavailable();
        } catch (RestClientException e) {
            log.error("Razorpay call failed: {}", e.getMessage());
            throw unavailable();
        }
    }

    private static String truncate(String s) { return s == null ? "" : s.substring(0, Math.min(300, s.length())); }
    private static BusinessException unavailable() { return new BusinessException("GATEWAY_ERROR", "The payment provider could not be reached. Please try again in a moment.", HttpStatus.BAD_GATEWAY); }
}
