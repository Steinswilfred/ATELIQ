package com.clothingplatform.assignment;

import com.clothingplatform.assignment.AssignmentService.*;
import com.clothingplatform.common.ApiResponse;
import com.clothingplatform.security.CurrentUser;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class AssignmentController {
    public record AssignRequest(Long tailorUserId, String reason) {}
    private final AssignmentService service;
    public AssignmentController(AssignmentService service) { this.service = service; }
    private static long uid() { return CurrentUser.require().getId(); }

    // tailor (the /tailor/** path already requires the TAILOR role)
    @GetMapping("/tailor/orders/available")
    public ApiResponse<List<AvailableOrder>> available() { return ApiResponse.ok(service.available(uid())); }

    @PostMapping("/tailor/orders/{id}/accept")
    public ApiResponse<Void> accept(@PathVariable long id) { service.accept(uid(), id); return ApiResponse.ok(null); }

    @GetMapping("/tailor/orders")
    public ApiResponse<List<TailorOrderRow>> mine(@RequestParam(defaultValue = "active") String scope) { return ApiResponse.ok(service.mine(uid(), !"completed".equals(scope))); }

    @GetMapping("/tailor/orders/{id}")
    public ApiResponse<TailorOrderDetail> detail(@PathVariable long id) { return ApiResponse.ok(service.tailorDetail(uid(), id)); }

    // admin
    @GetMapping("/admin/orders/{id}/assignment") @PreAuthorize("hasAuthority('ORDER_VIEW')")
    public ApiResponse<AssignmentInfo> info(@PathVariable long id) { return ApiResponse.ok(service.info(id)); }

    @PostMapping("/admin/orders/{id}/assignment") @PreAuthorize("hasAuthority('ORDER_MANAGE')")
    public ApiResponse<Void> assign(@PathVariable long id, @RequestBody AssignRequest r) { service.adminAssign(uid(), id, r.tailorUserId(), r.reason()); return ApiResponse.ok(null); }
}
