package com.clothingplatform.shop;

import com.clothingplatform.common.ApiResponse;
import com.clothingplatform.security.CurrentUser;
import com.clothingplatform.shop.DesignService.*;
import com.clothingplatform.shop.MeasurementService.*;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** A customer's own measurement profiles and saved designs. Every query is scoped to the signed-in user. */
@RestController
@RequestMapping("/api/v1/me")
@PreAuthorize("hasRole('CUSTOMER')")
public class MeShopController {
    private final MeasurementService measurements;
    private final DesignService designs;
    public MeShopController(MeasurementService measurements, DesignService designs) { this.measurements = measurements; this.designs = designs; }

    private static long uid() { return CurrentUser.require().getId(); }

    @GetMapping("/measurements")
    public ApiResponse<List<ProfileView>> profiles() { return ApiResponse.ok(measurements.list(uid())); }

    @PostMapping("/measurements") @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Map<String, Long>> createProfile(@Valid @RequestBody ProfileRequest r) { return ApiResponse.ok(Map.of("id", measurements.create(uid(), r))); }

    @PutMapping("/measurements/{id}")
    public ApiResponse<Void> updateProfile(@PathVariable long id, @Valid @RequestBody ProfileRequest r) { measurements.update(uid(), id, r); return ApiResponse.ok(null); }

    @DeleteMapping("/measurements/{id}")
    public ApiResponse<Void> deleteProfile(@PathVariable long id) { measurements.delete(uid(), id); return ApiResponse.ok(null); }

    @GetMapping("/designs")
    public ApiResponse<List<DesignView>> designs() { return ApiResponse.ok(designs.list(uid())); }

    @GetMapping("/designs/{id}")
    public ApiResponse<DesignDetail> design(@PathVariable long id) { return ApiResponse.ok(designs.get(uid(), id)); }

    @PostMapping("/designs") @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Map<String, Long>> createDesign(@Valid @RequestBody DesignRequest r) { return ApiResponse.ok(Map.of("id", designs.create(uid(), r))); }

    @PutMapping("/designs/{id}")
    public ApiResponse<Void> updateDesign(@PathVariable long id, @Valid @RequestBody DesignRequest r) { designs.update(uid(), id, r); return ApiResponse.ok(null); }

    @DeleteMapping("/designs/{id}")
    public ApiResponse<Void> deleteDesign(@PathVariable long id) { designs.delete(uid(), id); return ApiResponse.ok(null); }
}
