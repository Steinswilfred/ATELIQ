package com.clothingplatform.auth;

import com.clothingplatform.auth.AuthDtos.*;
import com.clothingplatform.common.ApiResponse;
import com.clothingplatform.security.AppUserDetailsService;
import com.clothingplatform.security.CurrentUser;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class AuthController {
    private static final String COOKIE = "refresh_token";
    private static final String COOKIE_PATH = "/api/v1/auth";

    private final AuthService auth;
    private final AppUserDetailsService details;
    private final RefreshTokenService refreshTokens;
    private final boolean cookieSecure;

    public AuthController(AuthService auth, AppUserDetailsService details, RefreshTokenService refreshTokens,
                          @Value("${app.jwt.cookie-secure}") boolean cookieSecure) {
        this.auth = auth; this.details = details; this.refreshTokens = refreshTokens; this.cookieSecure = cookieSecure;
    }

    private static final Map<String, String> OK = Map.of("status", "ok");

    @PostMapping("/auth/register/customer")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Map<String, String>> registerCustomer(@Valid @RequestBody RegisterCustomerRequest r, HttpServletRequest req) {
        auth.registerCustomer(r, req.getRemoteAddr());
        return ApiResponse.ok(OK);
    }

    @PostMapping("/auth/register/tailor")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Map<String, String>> registerTailor(@Valid @RequestBody RegisterTailorRequest r, HttpServletRequest req) {
        auth.registerTailor(r, req.getRemoteAddr());
        return ApiResponse.ok(OK);
    }

    @PostMapping("/auth/verify-email")
    public ApiResponse<Map<String, String>> verifyEmail(@Valid @RequestBody TokenRequest r) { auth.verifyEmail(r.token()); return ApiResponse.ok(OK); }

    @PostMapping("/auth/verify-phone")
    public ApiResponse<Map<String, String>> verifyPhone(@Valid @RequestBody PhoneVerifyRequest r) { auth.verifyPhone(r.email(), r.otp()); return ApiResponse.ok(OK); }

    @PostMapping("/auth/resend-email")
    public ApiResponse<Map<String, String>> resendEmail(@Valid @RequestBody EmailRequest r) { auth.resendEmailVerification(r.email()); return ApiResponse.ok(OK); }

    @PostMapping("/auth/resend-phone-otp")
    public ApiResponse<Map<String, String>> resendOtp(@Valid @RequestBody EmailRequest r) { auth.resendPhoneOtp(r.email()); return ApiResponse.ok(OK); }

    @PostMapping("/auth/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest r, HttpServletRequest req) {
        AuthResult res = auth.login(r, req.getRemoteAddr(), req.getHeader(HttpHeaders.USER_AGENT));
        return withCookie(res);
    }

    @PostMapping("/auth/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(HttpServletRequest req) {
        return withCookie(auth.refresh(readCookie(req), req.getRemoteAddr(), req.getHeader(HttpHeaders.USER_AGENT)));
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<ApiResponse<Map<String, String>>> logout(HttpServletRequest req) {
        auth.logout(readCookie(req));
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString()).body(ApiResponse.ok(OK));
    }

    @PostMapping("/auth/forgot-password")
    public ApiResponse<Map<String, String>> forgot(@Valid @RequestBody EmailRequest r) { auth.forgotPassword(r.email()); return ApiResponse.ok(OK); }

    @PostMapping("/auth/reset-password")
    public ApiResponse<Map<String, String>> reset(@Valid @RequestBody ResetPasswordRequest r) { auth.resetPassword(r); return ApiResponse.ok(OK); }

    // ----- authenticated -----

    @GetMapping("/me")
    public ApiResponse<UserSummary> me() {
        return ApiResponse.ok(auth.summary(details.loadById(CurrentUser.require().getId()).orElseThrow()));
    }

    @PostMapping("/me/password")
    public ApiResponse<Map<String, String>> changePassword(@Valid @RequestBody ChangePasswordRequest r) {
        auth.changePassword(CurrentUser.require().getId(), r);
        return ApiResponse.ok(OK);
    }

    // ----- helpers -----

    private ResponseEntity<ApiResponse<AuthResponse>> withCookie(AuthResult res) {
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie(res.refreshToken(), refreshTokens.ttl()).toString())
                .body(ApiResponse.ok(res.body()));
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(COOKIE, value).httpOnly(true).secure(cookieSecure).sameSite("Strict")
                .path(COOKIE_PATH).maxAge(maxAge).build();
    }

    private String readCookie(HttpServletRequest req) {
        if (req.getCookies() == null) return null;
        for (Cookie c : req.getCookies()) if (COOKIE.equals(c.getName())) return c.getValue();
        return null;
    }
}
