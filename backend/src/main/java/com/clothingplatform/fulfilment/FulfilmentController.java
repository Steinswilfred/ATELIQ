package com.clothingplatform.fulfilment;

import com.clothingplatform.common.ApiResponse;
import com.clothingplatform.fulfilment.DeliveryService.*;
import com.clothingplatform.fulfilment.ProgressService.ProgressRequest;
import com.clothingplatform.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class FulfilmentController {
    private final ProgressService progress;
    private final DeliveryService delivery;
    public FulfilmentController(ProgressService progress, DeliveryService delivery) { this.progress = progress; this.delivery = delivery; }
    private static long uid() { return CurrentUser.require().getId(); }

    // tailor (the /tailor/** path already requires the TAILOR role)
    @PostMapping("/tailor/orders/{id}/progress")
    public ApiResponse<Void> advance(@PathVariable long id, @Valid @RequestBody ProgressRequest r) { progress.advance(uid(), id, r); return ApiResponse.ok(null); }

    @PostMapping("/tailor/orders/{id}/progress/upload")
    public ApiResponse<Map<String, String>> upload(@PathVariable long id, @RequestParam("file") MultipartFile file) { return ApiResponse.ok(Map.of("url", progress.uploadPhoto(uid(), id, file))); }

    // customer
    @GetMapping("/me/orders/{id}/delivery") @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<DeliveryView> customerDelivery(@PathVariable long id) { return ApiResponse.ok(delivery.forCustomer(uid(), id)); }

    // admin
    @GetMapping("/admin/orders/{id}/delivery") @PreAuthorize("hasAuthority('ORDER_VIEW')")
    public ApiResponse<DeliveryView> adminDelivery(@PathVariable long id) { return ApiResponse.ok(delivery.forAdmin(id)); }

    @PostMapping("/admin/orders/{id}/delivery/ship") @PreAuthorize("hasAuthority('DELIVERY_MANAGE')")
    public ApiResponse<Void> ship(@PathVariable long id, @Valid @RequestBody ShipRequest r) { delivery.ship(uid(), id, r); return ApiResponse.ok(null); }

    @PostMapping("/admin/orders/{id}/delivery/status") @PreAuthorize("hasAuthority('DELIVERY_MANAGE')")
    public ApiResponse<Void> status(@PathVariable long id, @Valid @RequestBody StatusRequest r) { delivery.update(uid(), id, r); return ApiResponse.ok(null); }

    @PostMapping("/admin/orders/{id}/delivery/events") @PreAuthorize("hasAuthority('DELIVERY_MANAGE')")
    public ApiResponse<Void> event(@PathVariable long id, @Valid @RequestBody EventRequest r) { delivery.addEvent(uid(), id, r); return ApiResponse.ok(null); }
}
