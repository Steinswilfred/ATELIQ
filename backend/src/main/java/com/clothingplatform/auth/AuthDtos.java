package com.clothingplatform.auth;

import com.clothingplatform.user.UserType;
import jakarta.validation.constraints.*;
import java.util.List;

public final class AuthDtos {
    private AuthDtos() {}

    public static final String PASSWORD_RULE = "^(?=.*[A-Za-z])(?=.*\\d).{8,72}$";
    public static final String PASSWORD_MSG = "Use 8 to 72 characters with at least one letter and one number.";
    public static final String PHONE_RULE = "^\\+[1-9]\\d{7,14}$";
    public static final String PHONE_MSG = "Enter the phone number with country code, for example +919876543210.";

    public record RegisterCustomerRequest(
            @NotBlank @Size(max = 160) String fullName,
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Pattern(regexp = PHONE_RULE, message = PHONE_MSG) String phone,
            @NotBlank @Pattern(regexp = PASSWORD_RULE, message = PASSWORD_MSG) String password) {}

    public record RegisterTailorRequest(
            @NotBlank @Size(max = 160) String fullName,
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Pattern(regexp = PHONE_RULE, message = PHONE_MSG) String phone,
            @NotBlank @Pattern(regexp = PASSWORD_RULE, message = PASSWORD_MSG) String password,
            @NotBlank @Size(max = 500) String address,
            @Min(0) @Max(80) int experienceYears,
            @Size(max = 4000) String previousExperience,
            @Size(max = 80) String preferredCategory,
            @Size(max = 2000) String skills) {}

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}
    public record EmailRequest(@NotBlank @Email String email) {}
    public record TokenRequest(@NotBlank String token) {}
    public record PhoneVerifyRequest(@NotBlank @Email String email, @NotBlank @Pattern(regexp = "^\\d{6}$", message = "Enter the 6-digit code.") String otp) {}
    public record ResetPasswordRequest(@NotBlank String token, @NotBlank @Pattern(regexp = PASSWORD_RULE, message = PASSWORD_MSG) String newPassword) {}
    public record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank @Pattern(regexp = PASSWORD_RULE, message = PASSWORD_MSG) String newPassword) {}

    public record UserSummary(long id, String fullName, String email, UserType userType, List<String> permissions) {}
    public record AuthResponse(String accessToken, long expiresInSeconds, UserSummary user) {}
    public record AuthResult(AuthResponse body, String refreshToken) {}
}
