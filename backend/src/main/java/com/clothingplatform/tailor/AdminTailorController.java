package com.clothingplatform.tailor;

import com.clothingplatform.common.ApiResponse;
import com.clothingplatform.common.PageResult;
import com.clothingplatform.tailor.TailorService.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminTailorController {
    public record CategoryAssign(@NotNull Long categoryId) {}
    private final TailorService service;
    public AdminTailorController(TailorService service) { this.service = service; }

    // categories: the picker on the approval screen needs the list, so reviewers can read it too
    @GetMapping("/tailor-categories") @PreAuthorize("hasAnyAuthority('TAILOR_CATEGORY_MANAGE','TAILOR_APPLICATION_REVIEW','USER_VIEW')")
    public ApiResponse<List<CategoryView>> categories() { return ApiResponse.ok(service.categories()); }

    @PostMapping("/tailor-categories") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAuthority('TAILOR_CATEGORY_MANAGE')")
    public ApiResponse<Map<String, Long>> create(@Valid @RequestBody CategoryRequest r) { return ApiResponse.ok(Map.of("id", service.createCategory(r))); }

    @PutMapping("/tailor-categories/{id}") @PreAuthorize("hasAuthority('TAILOR_CATEGORY_MANAGE')")
    public ApiResponse<Void> update(@PathVariable long id, @Valid @RequestBody CategoryRequest r) { service.updateCategory(id, r); return ApiResponse.ok(null); }

    @DeleteMapping("/tailor-categories/{id}") @PreAuthorize("hasAuthority('TAILOR_CATEGORY_MANAGE')")
    public ApiResponse<Void> delete(@PathVariable long id) { service.deleteCategory(id); return ApiResponse.ok(null); }

    // tailors
    @GetMapping("/tailors") @PreAuthorize("hasAuthority('USER_VIEW')")
    public ApiResponse<PageResult<TailorRow>> list(@RequestParam(required = false) Long categoryId, @RequestParam(required = false) String availability,
                                                   @RequestParam(required = false) String status, @RequestParam(required = false) String q,
                                                   @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.list(categoryId, availability, status, q, page, size));
    }

    @GetMapping("/tailors/{userId}") @PreAuthorize("hasAuthority('USER_VIEW')")
    public ApiResponse<TailorDetail> detail(@PathVariable long userId) { return ApiResponse.ok(service.detail(userId)); }

    @PutMapping("/tailors/{userId}/category") @PreAuthorize("hasAuthority('TAILOR_CATEGORY_MANAGE')")
    public ApiResponse<Void> category(@PathVariable long userId, @Valid @RequestBody CategoryAssign r) { service.setCategory(userId, r.categoryId()); return ApiResponse.ok(null); }

    @PutMapping("/tailors/{userId}/availability") @PreAuthorize("hasAuthority('TAILOR_CATEGORY_MANAGE')")
    public ApiResponse<Void> availability(@PathVariable long userId, @Valid @RequestBody AvailabilityRequest r) { service.setAvailabilityAsAdmin(userId, r); return ApiResponse.ok(null); }
}
