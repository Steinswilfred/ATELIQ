package com.clothingplatform.payment;

import com.clothingplatform.common.ApiResponse;
import com.clothingplatform.common.PageResult;
import com.clothingplatform.payment.PaymentService.*;
import com.clothingplatform.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
public class PaymentController {
    private final PaymentService service;
    public PaymentController(PaymentService service) { this.service = service; }

    /** Browser callback after Razorpay Checkout. The server re-verifies everything; the browser's word is never trusted. */
    @PostMapping("/api/v1/me/payments/razorpay/verify") @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<VerifyResult> verify(@Valid @RequestBody VerifyRequest r) { return ApiResponse.ok(service.verify(CurrentUser.require().getId(), r)); }

    /** Razorpay -> us. Public, authenticated only by the HMAC signature of the raw body. */
    @PostMapping("/api/v1/payments/razorpay/webhook")
    public ResponseEntity<Void> webhook(@RequestBody String body, @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature,
                                        @RequestHeader(value = "X-Razorpay-Event-Id", required = false) String eventId) {
        return service.handleWebhook(body, signature, eventId) ? ResponseEntity.ok().build() : ResponseEntity.badRequest().build();
    }

    @GetMapping("/api/v1/admin/payments") @PreAuthorize("hasAuthority('PAYMENT_VIEW')")
    public ApiResponse<PageResult<PaymentRow>> list(@RequestParam(required = false) String status, @RequestParam(required = false) String method, @RequestParam(required = false) String q,
                                                    @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) { return ApiResponse.ok(service.list(status, method, q, page, size)); }

    @GetMapping("/api/v1/admin/payments/{id}") @PreAuthorize("hasAuthority('PAYMENT_VIEW')")
    public ApiResponse<PaymentDetail> detail(@PathVariable long id) { return ApiResponse.ok(service.detail(id)); }

    @PostMapping("/api/v1/admin/payments/{id}/collect-cod") @PreAuthorize("hasAuthority('PAYMENT_COD_COLLECT')")
    public ApiResponse<Void> collect(@PathVariable long id) { service.collectCod(id); return ApiResponse.ok(null); }
}
