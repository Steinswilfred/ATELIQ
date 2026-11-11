package com.clothingplatform.role;

import com.clothingplatform.common.ApiResponse;
import com.clothingplatform.role.RbacDtos.*;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminRbacController {
    private final RbacService rbac;
    public AdminRbacController(RbacService rbac) { this.rbac = rbac; }

    @GetMapping("/roles") @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    public ApiResponse<List<RoleView>> roles() { return ApiResponse.ok(rbac.listRoles()); }

    @PostMapping("/roles") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    public ApiResponse<RoleView> createRole(@Valid @RequestBody CreateRoleRequest r) { return ApiResponse.ok(rbac.createRole(r)); }

    @PutMapping("/roles/{id}") @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    public ApiResponse<Void> updateRole(@PathVariable long id, @Valid @RequestBody UpdateRoleRequest r) { rbac.updateRole(id, r); return ApiResponse.ok(null); }

    @DeleteMapping("/roles/{id}") @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    public ApiResponse<Void> deleteRole(@PathVariable long id) { rbac.deleteRole(id); return ApiResponse.ok(null); }

    @PutMapping("/roles/{id}/permissions") @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    public ApiResponse<Void> setPermissions(@PathVariable long id, @Valid @RequestBody SetPermissionsRequest r) { rbac.setRolePermissions(id, r.permissionCodes()); return ApiResponse.ok(null); }

    @GetMapping("/permissions") @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    public ApiResponse<List<PermissionView>> permissions() { return ApiResponse.ok(rbac.listPermissions()); }

    @PostMapping("/permissions") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    public ApiResponse<PermissionView> createPermission(@Valid @RequestBody CreatePermissionRequest r) { return ApiResponse.ok(rbac.createPermission(r)); }

    @PutMapping("/users/{id}/roles") @PreAuthorize("hasAuthority('ADMIN_MANAGE')")
    public ApiResponse<Void> assignRoles(@PathVariable long id, @Valid @RequestBody AssignRolesRequest r) { rbac.assignRoles(id, r.roleCodes()); return ApiResponse.ok(null); }

    @PostMapping("/admins") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAuthority('ADMIN_CREATE')")
    public ApiResponse<Map<String, Long>> createAdmin(@Valid @RequestBody CreateAdminRequest r) { return ApiResponse.ok(Map.of("id", rbac.createAdmin(r))); }
}
