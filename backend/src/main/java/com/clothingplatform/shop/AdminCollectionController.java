package com.clothingplatform.shop;

import com.clothingplatform.common.ApiResponse;
import com.clothingplatform.shop.TrendService.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/collections")
public class AdminCollectionController {
    public record ProductIds(@NotNull List<Long> ids) {}
    private final TrendService service;
    public AdminCollectionController(TrendService service) { this.service = service; }

    @GetMapping @PreAuthorize("hasAuthority('PRODUCT_VIEW')")
    public ApiResponse<List<CollectionView>> list() { return ApiResponse.ok(service.list()); }

    @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    public ApiResponse<Map<String, Long>> create(@Valid @RequestBody CollectionRequest r) { return ApiResponse.ok(Map.of("id", service.create(r))); }

    @PutMapping("/{id}") @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    public ApiResponse<Void> update(@PathVariable long id, @Valid @RequestBody CollectionRequest r) { service.update(id, r); return ApiResponse.ok(null); }

    @DeleteMapping("/{id}") @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    public ApiResponse<Void> delete(@PathVariable long id) { service.delete(id); return ApiResponse.ok(null); }

    @PutMapping("/{id}/products") @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    public ApiResponse<Void> products(@PathVariable long id, @Valid @RequestBody ProductIds r) { service.setProducts(id, r.ids()); return ApiResponse.ok(null); }
}
