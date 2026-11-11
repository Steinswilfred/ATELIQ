package com.clothingplatform.role;

import com.clothingplatform.auth.AuthDtos;
import jakarta.validation.constraints.*;
import java.util.List;

public final class RbacDtos {
    private RbacDtos() {}
    public record RoleView(long id, String code, String name, String description, boolean system, List<String> permissions) {}
    public record PermissionView(long id, String code, String module, String description) {}
    public record CreateRoleRequest(@NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{2,59}$", message = "Use UPPER_SNAKE_CASE, 3 to 60 characters.") String code,
                                    @NotBlank @Size(max = 120) String name, @Size(max = 500) String description) {}
    public record UpdateRoleRequest(@NotBlank @Size(max = 120) String name, @Size(max = 500) String description) {}
    public record SetPermissionsRequest(@NotNull List<String> permissionCodes) {}
    public record CreatePermissionRequest(@NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{2,79}$", message = "Use UPPER_SNAKE_CASE.") String code,
                                          @NotBlank @Size(max = 60) String module, @Size(max = 500) String description) {}
    public record AssignRolesRequest(@NotNull List<String> roleCodes) {}
    public record CreateAdminRequest(@NotBlank @Size(max = 160) String fullName, @NotBlank @jakarta.validation.constraints.Email String email,
                                     @Pattern(regexp = AuthDtos.PHONE_RULE, message = AuthDtos.PHONE_MSG) String phone,
                                     @NotNull List<String> roleCodes) {}
}
