package com.clothingplatform.admin;

import com.clothingplatform.admin.AdminUserService.*;
import com.clothingplatform.common.ApiResponse;
import com.clothingplatform.common.PageResult;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {
    private final AdminUserService service;
    public AdminUserController(AdminUserService service) { this.service = service; }

    @GetMapping @PreAuthorize("hasAuthority('USER_VIEW')")
    public ApiResponse<PageResult<UserRow>> list(@RequestParam(required = false) String type, @RequestParam(required = false) String status,
                                                 @RequestParam(required = false) String q, @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.list(type, status, q, page, size));
    }

    @GetMapping("/{id}") @PreAuthorize("hasAuthority('USER_VIEW')")
    public ApiResponse<UserDetail> detail(@PathVariable long id) { return ApiResponse.ok(service.detail(id)); }

    @PutMapping("/{id}") @PreAuthorize("hasAuthority('USER_MANAGE')")
    public ApiResponse<Void> update(@PathVariable long id, @RequestBody UpdateUserRequest r) { service.update(id, r); return ApiResponse.ok(null); }

    @PostMapping("/{id}/status") @PreAuthorize("hasAuthority('USER_MANAGE')")
    public ApiResponse<Void> status(@PathVariable long id, @RequestBody StatusChangeRequest r) { service.changeStatus(id, r); return ApiResponse.ok(null); }
}
