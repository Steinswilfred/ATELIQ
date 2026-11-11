package com.clothingplatform.order;

import com.clothingplatform.common.ApiResponse;
import com.clothingplatform.common.PageResult;
import com.clothingplatform.order.CheckoutService.*;
import com.clothingplatform.order.OrderService.*;
import com.clothingplatform.security.CurrentUser;
import com.clothingplatform.settings.SettingsService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class OrderController {
    public record PreviewRequest(Long tailorCategoryId, String couponCode) {}

    private final CheckoutService checkout;
    private final OrderService orders;
    private final SettingsService settings;
    public OrderController(CheckoutService checkout, OrderService orders, SettingsService settings) { this.checkout = checkout; this.orders = orders; this.settings = settings; }
    private static long uid() { return CurrentUser.require().getId(); }

    @PostMapping("/me/checkout/preview") @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<Preview> preview(@RequestBody PreviewRequest r) { return ApiResponse.ok(checkout.preview(uid(), r.tailorCategoryId(), r.couponCode())); }

    @PostMapping("/me/checkout") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<Result> place(@Valid @RequestBody CheckoutRequest r) { return ApiResponse.ok(checkout.checkout(uid(), r)); }

    @PostMapping("/me/orders/{id}/pay") @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<Result> retry(@PathVariable long id) { return ApiResponse.ok(checkout.retryPayment(uid(), id)); }

    @GetMapping("/me/orders") @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<PageResult<OrderSummary>> mine(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) { return ApiResponse.ok(orders.listForUser(uid(), page, size)); }

    @GetMapping("/me/orders/{id}") @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<OrderDetail> mineDetail(@PathVariable long id) { return ApiResponse.ok(orders.detail(uid(), id)); }

    @GetMapping(value = "/me/orders/{id}/invoice", produces = MediaType.TEXT_HTML_VALUE) @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<String> invoice(@PathVariable long id) { return html(orders.invoiceHtml(uid(), id, shopName())); }

    @GetMapping("/admin/orders") @PreAuthorize("hasAuthority('ORDER_VIEW')")
    public ApiResponse<PageResult<OrderSummary>> adminList(@RequestParam(required = false) String status, @RequestParam(required = false) String q,
                                                           @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) { return ApiResponse.ok(orders.adminList(status, q, page, size)); }

    @GetMapping("/admin/orders/{id}") @PreAuthorize("hasAuthority('ORDER_VIEW')")
    public ApiResponse<OrderDetail> adminDetail(@PathVariable long id) { return ApiResponse.ok(orders.detail(null, id)); }

    @GetMapping(value = "/admin/orders/{id}/invoice", produces = MediaType.TEXT_HTML_VALUE) @PreAuthorize("hasAuthority('ORDER_VIEW')")
    public ResponseEntity<String> adminInvoice(@PathVariable long id) { return html(orders.invoiceHtml(null, id, shopName())); }

    private String shopName() { var n = settings.publicSettings().get("site.name"); return n == null ? "Atelier" : n.asText("Atelier"); }

    private static ResponseEntity<String> html(String body) {
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).cacheControl(CacheControl.noStore())
                .header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; script-src 'unsafe-inline'").body(body);
    }
}
