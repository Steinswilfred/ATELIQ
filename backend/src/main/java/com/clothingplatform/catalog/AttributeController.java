package com.clothingplatform.catalog;

import com.clothingplatform.catalog.AttributeService.*;
import com.clothingplatform.catalog.CategoryService.*;
import com.clothingplatform.common.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
public class AttributeController {
    private static final String READ = "hasAnyAuthority('PRODUCT_VIEW','FABRIC_MANAGE','PRODUCT_WRITE')";
    private static final String WRITE = "hasAuthority('FABRIC_MANAGE')";
    private final AttributeService attrs;
    private final CategoryService categories;
    public AttributeController(AttributeService attrs, CategoryService categories) { this.attrs = attrs; this.categories = categories; }

    private static Map<String, Long> id(long id) { return Map.of("id", id); }

    // categories
    @GetMapping("/categories") @PreAuthorize("hasAnyAuthority('PRODUCT_VIEW','CATEGORY_MANAGE','PRODUCT_WRITE')")
    public ApiResponse<List<CategoryView>> categories() { return ApiResponse.ok(categories.list()); }
    @PostMapping("/categories") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAuthority('CATEGORY_MANAGE')")
    public ApiResponse<Map<String, Long>> createCategory(@Valid @RequestBody CategoryRequest r) { return ApiResponse.ok(id(categories.create(r))); }
    @PutMapping("/categories/{id}") @PreAuthorize("hasAuthority('CATEGORY_MANAGE')")
    public ApiResponse<Void> updateCategory(@PathVariable long id, @Valid @RequestBody CategoryRequest r) { categories.update(id, r); return ApiResponse.ok(null); }
    @DeleteMapping("/categories/{id}") @PreAuthorize("hasAuthority('CATEGORY_MANAGE')")
    public ApiResponse<Void> deleteCategory(@PathVariable long id) { categories.delete(id); return ApiResponse.ok(null); }

    // fabrics
    @GetMapping("/fabrics") @PreAuthorize(READ) public ApiResponse<List<FabricView>> fabrics() { return ApiResponse.ok(attrs.fabrics()); }
    @PostMapping("/fabrics") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize(WRITE)
    public ApiResponse<Map<String, Long>> createFabric(@Valid @RequestBody FabricRequest r) { return ApiResponse.ok(id(attrs.createFabric(r))); }
    @PutMapping("/fabrics/{id}") @PreAuthorize(WRITE)
    public ApiResponse<Void> updateFabric(@PathVariable long id, @Valid @RequestBody FabricRequest r) { attrs.updateFabric(id, r); return ApiResponse.ok(null); }
    @DeleteMapping("/fabrics/{id}") @PreAuthorize(WRITE)
    public ApiResponse<Void> deleteFabric(@PathVariable long id) { attrs.deleteFabric(id); return ApiResponse.ok(null); }

    // colours
    @GetMapping("/colors") @PreAuthorize(READ) public ApiResponse<List<ColorView>> colors() { return ApiResponse.ok(attrs.colors()); }
    @PostMapping("/colors") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize(WRITE)
    public ApiResponse<Map<String, Long>> createColor(@Valid @RequestBody ColorRequest r) { return ApiResponse.ok(id(attrs.createColor(r))); }
    @PutMapping("/colors/{id}") @PreAuthorize(WRITE)
    public ApiResponse<Void> updateColor(@PathVariable long id, @Valid @RequestBody ColorRequest r) { attrs.updateColor(id, r); return ApiResponse.ok(null); }
    @DeleteMapping("/colors/{id}") @PreAuthorize(WRITE)
    public ApiResponse<Void> deleteColor(@PathVariable long id) { attrs.deleteColor(id); return ApiResponse.ok(null); }

    // sizes
    @GetMapping("/sizes") @PreAuthorize(READ) public ApiResponse<List<SizeView>> sizes() { return ApiResponse.ok(attrs.sizes()); }
    @PostMapping("/sizes") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize(WRITE)
    public ApiResponse<Map<String, Long>> createSize(@Valid @RequestBody SizeRequest r) { return ApiResponse.ok(id(attrs.createSize(r))); }
    @PutMapping("/sizes/{id}") @PreAuthorize(WRITE)
    public ApiResponse<Void> updateSize(@PathVariable long id, @Valid @RequestBody SizeRequest r) { attrs.updateSize(id, r); return ApiResponse.ok(null); }
    @DeleteMapping("/sizes/{id}") @PreAuthorize(WRITE)
    public ApiResponse<Void> deleteSize(@PathVariable long id) { attrs.deleteSize(id); return ApiResponse.ok(null); }
}
