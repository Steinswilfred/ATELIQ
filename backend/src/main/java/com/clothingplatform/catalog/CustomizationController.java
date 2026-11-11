package com.clothingplatform.catalog;

import com.clothingplatform.catalog.CustomizationService.*;
import com.clothingplatform.common.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/customization")
public class CustomizationController {
    private final CustomizationService service;
    public CustomizationController(CustomizationService service) { this.service = service; }

    @GetMapping("/groups") @PreAuthorize("hasAnyAuthority('CUSTOMIZATION_MANAGE','PRODUCT_VIEW','PRODUCT_WRITE')")
    public ApiResponse<List<GroupView>> groups() { return ApiResponse.ok(service.groups()); }

    @PostMapping("/groups") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAuthority('CUSTOMIZATION_MANAGE')")
    public ApiResponse<Map<String, Long>> createGroup(@Valid @RequestBody GroupRequest r) { return ApiResponse.ok(Map.of("id", service.createGroup(r))); }

    @PutMapping("/groups/{id}") @PreAuthorize("hasAuthority('CUSTOMIZATION_MANAGE')")
    public ApiResponse<Void> updateGroup(@PathVariable long id, @Valid @RequestBody GroupRequest r) { service.updateGroup(id, r); return ApiResponse.ok(null); }

    @DeleteMapping("/groups/{id}") @PreAuthorize("hasAuthority('CUSTOMIZATION_MANAGE')")
    public ApiResponse<Void> deleteGroup(@PathVariable long id) { service.deleteGroup(id); return ApiResponse.ok(null); }

    @PostMapping("/options") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAuthority('CUSTOMIZATION_MANAGE')")
    public ApiResponse<Map<String, Long>> createOption(@Valid @RequestBody OptionRequest r) { return ApiResponse.ok(Map.of("id", service.createOption(r))); }

    @PutMapping("/options/{id}") @PreAuthorize("hasAuthority('CUSTOMIZATION_MANAGE')")
    public ApiResponse<Void> updateOption(@PathVariable long id, @Valid @RequestBody OptionRequest r) { service.updateOption(id, r); return ApiResponse.ok(null); }

    @DeleteMapping("/options/{id}") @PreAuthorize("hasAuthority('CUSTOMIZATION_MANAGE')")
    public ApiResponse<Void> deleteOption(@PathVariable long id) { service.deleteOption(id); return ApiResponse.ok(null); }
}
