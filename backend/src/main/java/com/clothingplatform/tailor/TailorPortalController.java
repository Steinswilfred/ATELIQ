package com.clothingplatform.tailor;

import com.clothingplatform.common.ApiResponse;
import com.clothingplatform.security.CurrentUser;
import com.clothingplatform.storage.StorageService;
import com.clothingplatform.tailor.TailorService.*;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** A tailor's own pages. The /api/v1/tailor/** path already requires the TAILOR role; every query is scoped to the signed-in tailor. */
@RestController
@RequestMapping("/api/v1/tailor")
public class TailorPortalController {
    private final TailorService service;
    private final StorageService storage;
    public TailorPortalController(TailorService service, StorageService storage) { this.service = service; this.storage = storage; }

    private static long uid() { return CurrentUser.require().getId(); }

    @GetMapping("/dashboard")
    public ApiResponse<Dashboard> dashboard() { return ApiResponse.ok(service.dashboard(uid())); }

    @GetMapping("/profile")
    public ApiResponse<TailorDetail> profile() { return ApiResponse.ok(service.myProfile(uid())); }

    @PutMapping("/profile")
    public ApiResponse<Void> update(@Valid @RequestBody ProfileUpdate r) { service.updateMyProfile(uid(), r); return ApiResponse.ok(null); }

    @PutMapping("/availability")
    public ApiResponse<Void> availability(@Valid @RequestBody AvailabilityRequest r) { service.setAvailabilityAsTailor(uid(), r); return ApiResponse.ok(null); }

    @PostMapping("/uploads/photo")
    public ApiResponse<Map<String, String>> photo(@RequestParam("file") MultipartFile file) { return ApiResponse.ok(Map.of("url", storage.storeImage("tailors", file))); }
}
