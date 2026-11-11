package com.clothingplatform.catalog;

import com.clothingplatform.catalog.ProductService.*;
import com.clothingplatform.common.ApiResponse;
import com.clothingplatform.common.PageResult;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/products")
public class ProductController {
    private final ProductService service;
    public ProductController(ProductService service) { this.service = service; }

    @GetMapping @PreAuthorize("hasAuthority('PRODUCT_VIEW')")
    public ApiResponse<PageResult<ProductRow>> list(@RequestParam(required = false) Long categoryId, @RequestParam(required = false) String status,
                                                    @RequestParam(required = false) String q, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.list(categoryId, status, q, page, size));
    }

    @GetMapping("/{id}") @PreAuthorize("hasAuthority('PRODUCT_VIEW')")
    public ApiResponse<ProductDetail> detail(@PathVariable long id) { return ApiResponse.ok(service.detail(id)); }

    @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    public ApiResponse<Map<String, Long>> create(@Valid @RequestBody ProductRequest r) { return ApiResponse.ok(Map.of("id", service.create(r))); }

    @PutMapping("/{id}") @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    public ApiResponse<Void> update(@PathVariable long id, @Valid @RequestBody ProductRequest r) { service.update(id, r); return ApiResponse.ok(null); }

    @PostMapping("/{id}/status") @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    public ApiResponse<Void> status(@PathVariable long id, @Valid @RequestBody StatusRequest r) { service.setStatus(id, r.status()); return ApiResponse.ok(null); }

    @DeleteMapping("/{id}") @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    public ApiResponse<Void> delete(@PathVariable long id) { service.delete(id); return ApiResponse.ok(null); }

    @PutMapping("/{id}/sizes") @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    public ApiResponse<Void> sizes(@PathVariable long id, @Valid @RequestBody IdsRequest r) { service.setSizes(id, r.ids()); return ApiResponse.ok(null); }

    @PutMapping("/{id}/fabrics") @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    public ApiResponse<Void> fabrics(@PathVariable long id, @Valid @RequestBody IdsRequest r) { service.setFabrics(id, r.ids()); return ApiResponse.ok(null); }

    @PutMapping("/{id}/customization") @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    public ApiResponse<Void> customization(@PathVariable long id, @Valid @RequestBody CustomizationRequest r) { service.setCustomization(id, r); return ApiResponse.ok(null); }

    @PostMapping("/{id}/images") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    public ApiResponse<Map<String, Long>> addImage(@PathVariable long id, @Valid @RequestBody ImageRequest r) { return ApiResponse.ok(Map.of("id", service.addImage(id, r))); }

    @PostMapping("/{id}/images/{imageId}/primary") @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    public ApiResponse<Void> primary(@PathVariable long id, @PathVariable long imageId) { service.setPrimaryImage(id, imageId); return ApiResponse.ok(null); }

    @DeleteMapping("/{id}/images/{imageId}") @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    public ApiResponse<Void> deleteImage(@PathVariable long id, @PathVariable long imageId) { service.deleteImage(id, imageId); return ApiResponse.ok(null); }
}
