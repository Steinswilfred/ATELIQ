package com.clothingplatform.cart;

import com.clothingplatform.cart.CartService.*;
import com.clothingplatform.common.ApiResponse;
import com.clothingplatform.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/me/cart")
@PreAuthorize("hasRole('CUSTOMER')")
public class CartController {
    private final CartService service;
    public CartController(CartService service) { this.service = service; }
    private static long uid() { return CurrentUser.require().getId(); }

    @GetMapping public ApiResponse<CartView> view() { return ApiResponse.ok(service.view(uid())); }
    @GetMapping("/count") public ApiResponse<Map<String, Integer>> count() { return ApiResponse.ok(Map.of("count", service.garmentCount(uid()))); }

    @PostMapping("/items") @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Map<String, Long>> add(@Valid @RequestBody AddRequest r) { return ApiResponse.ok(Map.of("id", service.add(uid(), r))); }

    @PutMapping("/items/{id}") public ApiResponse<Void> update(@PathVariable long id, @Valid @RequestBody UpdateRequest r) { service.update(uid(), id, r); return ApiResponse.ok(null); }

    @DeleteMapping("/items/{id}") public ApiResponse<Void> remove(@PathVariable long id) { service.remove(uid(), id); return ApiResponse.ok(null); }
}
