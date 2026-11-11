package com.clothingplatform.coupon;

import com.clothingplatform.common.ApiResponse;
import com.clothingplatform.coupon.CouponService.*;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/coupons")
@PreAuthorize("hasAuthority('COUPON_MANAGE')")
public class CouponController {
    private final CouponService service;
    public CouponController(CouponService service) { this.service = service; }

    @GetMapping public ApiResponse<List<CouponView>> list() { return ApiResponse.ok(service.list()); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public ApiResponse<Map<String, Long>> create(@Valid @RequestBody CouponRequest r) { return ApiResponse.ok(Map.of("id", service.create(r))); }
    @PutMapping("/{id}") public ApiResponse<Void> update(@PathVariable long id, @Valid @RequestBody CouponRequest r) { service.update(id, r); return ApiResponse.ok(null); }
    @DeleteMapping("/{id}") public ApiResponse<Void> delete(@PathVariable long id) { service.delete(id); return ApiResponse.ok(null); }
}
