package com.clothingplatform.finance;

import com.clothingplatform.common.ApiResponse;
import com.clothingplatform.common.PageResult;
import com.clothingplatform.finance.EarningsService.*;
import com.clothingplatform.finance.FinanceReportService.FinanceReport;
import com.clothingplatform.finance.RefundService.*;
import com.clothingplatform.security.AppUserDetails;
import com.clothingplatform.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class FinanceController {
    public record CancelRequest(@Size(max = 400) String reason) {}
    public record AdminCancelRequest(@Size(max = 400) String reason, BigDecimal refundAmount) {}
    public record AdminRefundRequest(@NotNull BigDecimal amount, @Size(max = 400) String reason, boolean reverseTailorEarning) {}
    public record ReasonRequest(@Size(max = 300) String reason) {}
    public record ReferenceRequest(@Size(max = 100) String reference) {}
    public record PayoutRequest(@NotNull Long tailorUserId, @Size(max = 100) String reference, @Size(max = 300) String note) {}
    public record TailorEarnings(Summary summary, List<EarningRow> earnings, List<PayoutRow> payouts) {}

    private final RefundService refunds;
    private final EarningsService earnings;
    private final FinanceReportService reports;
    public FinanceController(RefundService refunds, EarningsService earnings, FinanceReportService reports) { this.refunds = refunds; this.earnings = earnings; this.reports = reports; }
    private static long uid() { return CurrentUser.require().getId(); }
    private static boolean can(String permission) { AppUserDetails u = CurrentUser.require(); return u.isSuperAdmin() || u.hasPermission(permission); }

    // ---- customer ----
    @GetMapping("/me/orders/{id}/cancellation") @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<CancelPreview> preview(@PathVariable long id) { return ApiResponse.ok(refunds.preview(uid(), id)); }

    @PostMapping("/me/orders/{id}/cancel") @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<Void> cancel(@PathVariable long id, @Valid @RequestBody CancelRequest r) { refunds.customerCancel(uid(), id, r.reason()); return ApiResponse.ok(null); }

    @GetMapping("/me/orders/{id}/refunds") @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<List<RefundView>> myRefunds(@PathVariable long id) { return ApiResponse.ok(refunds.forCustomer(uid(), id)); }

    // ---- admin: cancellations and refunds ----
    @PostMapping("/admin/orders/{id}/cancel") @PreAuthorize("hasAuthority('ORDER_MANAGE')")
    public ApiResponse<Void> adminCancel(@PathVariable long id, @Valid @RequestBody AdminCancelRequest r) { refunds.adminCancel(uid(), id, r.reason(), r.refundAmount(), can("REFUND_INITIATE")); return ApiResponse.ok(null); }

    @PostMapping("/admin/orders/{id}/refund") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAuthority('REFUND_INITIATE')")
    public ApiResponse<Map<String, Long>> adminRefund(@PathVariable long id, @Valid @RequestBody AdminRefundRequest r) { return ApiResponse.ok(Map.of("id", refunds.adminRefund(uid(), id, r.amount(), r.reason(), r.reverseTailorEarning()))); }

    @GetMapping("/admin/refunds") @PreAuthorize("hasAuthority('PAYMENT_VIEW')")
    public ApiResponse<PageResult<RefundView>> list(@RequestParam(required = false) String status, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) { return ApiResponse.ok(refunds.list(status, page, size)); }

    @PostMapping("/admin/refunds/{id}/approve") @PreAuthorize("hasAuthority('REFUND_APPROVE')")
    public ApiResponse<Void> approve(@PathVariable long id) { refunds.approve(uid(), CurrentUser.require().isSuperAdmin(), id); return ApiResponse.ok(null); }

    @PostMapping("/admin/refunds/{id}/reject") @PreAuthorize("hasAuthority('REFUND_APPROVE')")
    public ApiResponse<Void> reject(@PathVariable long id, @Valid @RequestBody ReasonRequest r) { refunds.reject(uid(), id, r.reason()); return ApiResponse.ok(null); }

    @PostMapping("/admin/refunds/{id}/retry") @PreAuthorize("hasAuthority('REFUND_APPROVE')")
    public ApiResponse<Void> retry(@PathVariable long id) { refunds.retry(uid(), id); return ApiResponse.ok(null); }

    @PostMapping("/admin/refunds/{id}/manual-processed") @PreAuthorize("hasAuthority('REFUND_APPROVE')")
    public ApiResponse<Void> manual(@PathVariable long id, @Valid @RequestBody ReferenceRequest r) { refunds.manualProcessed(uid(), id, r.reference()); return ApiResponse.ok(null); }

    // ---- tailor (the /tailor/** path already requires the TAILOR role) ----
    @GetMapping("/tailor/earnings")
    public ApiResponse<TailorEarnings> myEarnings() { return ApiResponse.ok(new TailorEarnings(earnings.summary(uid()), earnings.earnings(uid(), null, 100), earnings.payouts(uid()))); }

    // ---- admin: earnings, payouts, reports ----
    @GetMapping("/admin/earnings/balances") @PreAuthorize("hasAuthority('EARNINGS_MANAGE')")
    public ApiResponse<List<TailorBalance>> balances() { return ApiResponse.ok(earnings.balances()); }

    @GetMapping("/admin/earnings") @PreAuthorize("hasAuthority('EARNINGS_MANAGE')")
    public ApiResponse<List<EarningRow>> earningsList(@RequestParam(required = false) Long tailorId, @RequestParam(required = false) String status) { return ApiResponse.ok(earnings.earnings(tailorId, status, 200)); }

    @GetMapping("/admin/payouts") @PreAuthorize("hasAuthority('EARNINGS_MANAGE')")
    public ApiResponse<List<PayoutRow>> payouts(@RequestParam(required = false) Long tailorId) { return ApiResponse.ok(earnings.payouts(tailorId)); }

    @PostMapping("/admin/payouts") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAuthority('EARNINGS_MANAGE')")
    public ApiResponse<Map<String, Long>> payout(@Valid @RequestBody PayoutRequest r) { return ApiResponse.ok(Map.of("id", earnings.recordPayout(uid(), r.tailorUserId(), r.reference(), r.note()))); }

    @GetMapping("/admin/reports/finance") @PreAuthorize("hasAuthority('REPORT_VIEW')")
    public ApiResponse<FinanceReport> finance(@RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate from,
                                              @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate to) { return ApiResponse.ok(reports.report(from, to)); }
}
