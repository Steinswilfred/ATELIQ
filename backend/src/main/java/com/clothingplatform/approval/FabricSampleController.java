package com.clothingplatform.approval;

import com.clothingplatform.approval.FabricSampleService.*;
import com.clothingplatform.common.ApiResponse;
import com.clothingplatform.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class FabricSampleController {
    private final FabricSampleService service;
    public FabricSampleController(FabricSampleService service) { this.service = service; }
    private static long uid() { return CurrentUser.require().getId(); }

    // tailor (the /tailor/** path already requires the TAILOR role)
    @PostMapping("/tailor/orders/{id}/samples/upload")
    public ApiResponse<Map<String, String>> upload(@PathVariable long id, @RequestParam("file") MultipartFile file) { return ApiResponse.ok(Map.of("url", service.uploadPhoto(uid(), id, file))); }

    @PostMapping("/tailor/orders/{id}/samples") @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Map<String, Long>> submit(@PathVariable long id, @Valid @RequestBody SubmitRequest r) { return ApiResponse.ok(Map.of("id", service.submit(uid(), id, r))); }

    @GetMapping("/tailor/orders/{id}/samples")
    public ApiResponse<List<SampleView>> tailorHistory(@PathVariable long id) { return ApiResponse.ok(service.forTailor(uid(), id)); }

    // customer
    @GetMapping("/me/orders/{id}/samples") @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<List<SampleView>> customerHistory(@PathVariable long id) { return ApiResponse.ok(service.forCustomer(uid(), id)); }

    @PostMapping("/me/orders/{id}/samples/{sampleId}/approve") @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<Void> approve(@PathVariable long id, @PathVariable long sampleId) { service.approve(uid(), id, sampleId); return ApiResponse.ok(null); }

    @PostMapping("/me/orders/{id}/samples/{sampleId}/reject") @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<Void> reject(@PathVariable long id, @PathVariable long sampleId, @Valid @RequestBody RejectRequest r) { service.reject(uid(), id, sampleId, r.reason()); return ApiResponse.ok(null); }

    // admin: read-only. Only the customer can approve.
    @GetMapping("/admin/orders/{id}/samples") @PreAuthorize("hasAuthority('ORDER_VIEW')")
    public ApiResponse<List<SampleView>> adminHistory(@PathVariable long id) { return ApiResponse.ok(service.forAdmin(id)); }
}
