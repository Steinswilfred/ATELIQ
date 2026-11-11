package com.clothingplatform.finance;

import com.clothingplatform.exception.BusinessException;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Figures come only from the ledger and the order records, so they can be traced back. Dates are in Indian time. */
@Service
public class FinanceReportService {
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    public record DayRow(LocalDate day, String account, BigDecimal amount) {}
    public record FinanceReport(LocalDate from, LocalDate to, Map<String, BigDecimal> accounts, BigDecimal platformRevenue, BigDecimal outstandingTailorPayable, long deliveredOrders,
                                long cancelledOrders, long refundsProcessed, BigDecimal refundedTotal, List<DayRow> daily) {}

    private final JdbcTemplate jdbc;
    public FinanceReportService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public FinanceReport report(LocalDate from, LocalDate to) {
        LocalDate end = to == null ? LocalDate.now(IST) : to;
        LocalDate start = from == null ? end.minusDays(29) : from;
        if (start.isAfter(end)) throw new BusinessException("BAD_REQUEST", "The start date must not be after the end date.", HttpStatus.BAD_REQUEST);
        if (start.plusDays(366).isBefore(end)) throw new BusinessException("BAD_REQUEST", "Choose a range of up to one year.", HttpStatus.BAD_REQUEST);
        Timestamp a = Timestamp.from(start.atStartOfDay(IST).toInstant()), b = Timestamp.from(end.plusDays(1).atStartOfDay(IST).toInstant());

        Map<String, BigDecimal> accounts = new LinkedHashMap<>();
        for (String acc : List.of(LedgerService.CUSTOMER_PAYMENTS, LedgerService.ITEM_REVENUE, LedgerService.DELIVERY_REVENUE, LedgerService.PLATFORM_COMMISSION, LedgerService.TAX_COLLECTED, LedgerService.TAILOR_PAYABLE, LedgerService.REVENUE_REFUNDED)) accounts.put(acc, BigDecimal.ZERO.setScale(2));
        jdbc.query("SELECT account, SUM(amount) FROM ledger_entries WHERE created_at >= ? AND created_at < ? GROUP BY account", rs -> { accounts.put(rs.getString(1), rs.getBigDecimal(2)); }, a, b);

        BigDecimal revenue = accounts.get(LedgerService.ITEM_REVENUE).add(accounts.get(LedgerService.DELIVERY_REVENUE)).add(accounts.get(LedgerService.PLATFORM_COMMISSION)).subtract(accounts.get(LedgerService.REVENUE_REFUNDED));
        BigDecimal outstanding = jdbc.queryForObject("SELECT COALESCE(SUM(amount), 0) FROM ledger_entries WHERE account = 'TAILOR_PAYABLE'", BigDecimal.class);
        long delivered = count("SELECT count(*) FROM delivery_tracking WHERE delivered_at >= ? AND delivered_at < ?", a, b);
        long cancelled = count("SELECT count(*) FROM order_status_history WHERE status = 'CANCELLED' AND created_at >= ? AND created_at < ?", a, b);
        Map<String, Object> rf = jdbc.queryForMap("SELECT count(*) AS n, COALESCE(SUM(amount), 0) AS total FROM refunds WHERE status = 'PROCESSED' AND processed_at >= ? AND processed_at < ?", a, b);
        List<DayRow> daily = jdbc.query("SELECT (created_at AT TIME ZONE 'Asia/Kolkata')::date AS d, account, SUM(amount) FROM ledger_entries WHERE created_at >= ? AND created_at < ? GROUP BY d, account ORDER BY d, account",
                (rs, i) -> new DayRow(rs.getDate(1).toLocalDate(), rs.getString(2), rs.getBigDecimal(3)), a, b);
        return new FinanceReport(start, end, accounts, revenue, outstanding, delivered, cancelled, ((Number) rf.get("n")).longValue(), (BigDecimal) rf.get("total"), daily);
    }

    private long count(String sql, Object... args) { Long n = jdbc.queryForObject(sql, Long.class, args); return n == null ? 0 : n; }
}
