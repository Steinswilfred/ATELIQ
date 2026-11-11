package com.clothingplatform.admin;

import com.clothingplatform.admin.TailorApplicationService.*;
import com.clothingplatform.common.ApiResponse;
import com.clothingplatform.common.PageResult;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/tailor-applications")
@PreAuthorize("hasAuthority('TAILOR_APPLICATION_REVIEW')")
public class TailorApplicationController {
    private final TailorApplicationService service;
    public TailorApplicationController(TailorApplicationService service) { this.service = service; }

    public record ApproveRequest(Long categoryId, String notes) {}
    public record RejectRequest(String reason, String notes) {}
    public record MessageRequest(String message) {}
    public record NoteRequest(String notes) {}

    @GetMapping
    public ApiResponse<PageResult<ApplicationRow>> list(@RequestParam(required = false) String status, @RequestParam(required = false) String q,
                                                        @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.list(status, q, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<ApplicationDetail> detail(@PathVariable long id) { return ApiResponse.ok(service.detail(id)); }

    @PostMapping("/{id}/approve")
    public ApiResponse<Void> approve(@PathVariable long id, @RequestBody ApproveRequest r) { service.approve(id, r.categoryId(), r.notes()); return ApiResponse.ok(null); }

    @PostMapping("/{id}/reject")
    public ApiResponse<Void> reject(@PathVariable long id, @RequestBody RejectRequest r) { service.reject(id, r.reason(), r.notes()); return ApiResponse.ok(null); }

    @PostMapping("/{id}/request-info")
    public ApiResponse<Void> requestInfo(@PathVariable long id, @RequestBody MessageRequest r) { service.requestInfo(id, r.message()); return ApiResponse.ok(null); }

    @PostMapping("/{id}/notes")
    public ApiResponse<Void> note(@PathVariable long id, @RequestBody NoteRequest r) { service.addNote(id, r.notes()); return ApiResponse.ok(null); }
}
