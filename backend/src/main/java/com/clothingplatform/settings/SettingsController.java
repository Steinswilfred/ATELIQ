package com.clothingplatform.settings;

import com.clothingplatform.common.ApiResponse;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class SettingsController {
    private final SettingsService service;
    public SettingsController(SettingsService service) { this.service = service; }

    /** Served to the storefront so theme changes apply without a rebuild. */
    @GetMapping("/public/settings")
    public ApiResponse<Map<String, JsonNode>> publicSettings() { return ApiResponse.ok(service.publicSettings()); }

    @GetMapping("/admin/settings") @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    public ApiResponse<Map<String, JsonNode>> all() { return ApiResponse.ok(service.allSettings()); }

    @PutMapping("/admin/settings/{key}") @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    public ApiResponse<Void> put(@PathVariable String key, @RequestBody JsonNode value) { service.put(key, value); return ApiResponse.ok(null); }
}
