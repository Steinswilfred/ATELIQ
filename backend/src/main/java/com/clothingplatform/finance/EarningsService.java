package com.clothingplatform.finance;

import static com.clothingplatform.catalog.CatalogSupport.*;

import com.clothingplatform.audit.AuditService;
import com.clothingplatform.common.Sql;
import com.clothingplatform.exception.BusinessException;
import com.clothingplatform.finance.FinancialSplit.Split;
import com.clothingplatform.notification.NotificationService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Revenue is recognised when an order is delivered: its total is split into item revenue, commission, the tailor's net, tax and delivery in the ledger,
 * and the tailor's earning starts a settlement hold. Payouts are recorded by an administrator after they pay the tailor outside this system
 * (bank transfer); no money is moved automatically.
 */
@Service
public class EarningsService {
    public record EarningRow(long id, String orderNumber, BigDecimal gross, BigDecimal commissionRate, BigDecimal commission, BigDecimal net, String status, Instant accruedAt, Instant payableAfter, boolean payable, boolean needsRecovery) {}
    public record Summary(BigDecimal pending, BigDecimal payable, BigDecimal paid, BigDecimal commissionRatePercent) {}
    public record PayoutRow(long id, String tailorName, BigDecimal amount, String reference, String note, String paidBy, Instant paidAt) {}
    public record TailorBalance(long tailorUserId, String name, BigDecimal pending, BigDecimal payable, int payableOrders, int recoveryFlags) {}

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final FinanceSettings.Loader settings;
    private final LedgerService ledger;
    private final NotificationService notifications;
    private final AuditService audit;
    public EarningsService(JdbcTemplate jdbc, TransactionTemplate tx, FinanceSettings.Loader settings, LedgerService ledger, NotificationService notifications, AuditService audit) {
        this.jdbc = jdbc; this.tx = tx; this.settings = settings; this.ledger = ledger; this.notifications = notifications; this.audit = audit;
    }

    /** Called inside the delivery transaction. Safe to call twice: the unique order_id makes the second call do nothing. */
    public void accrueOnDelivery(long orderId) {
        Map<String, Object> o = jdbc.queryForList("SELECT tailor_id, subtotal, discount, tailor_charges, tax, delivery_charge FROM orders WHERE id = ?", orderId).stream().findFirst().orElseThrow(() -> notFound("Order"));
        if (o.get("tailor_id") == null) return;
        FinanceSettings f = settings.load();
        Split sp = FinancialSplit.compute((BigDecimal) o.get("subtotal"), (BigDecimal) o.get("discount"), (BigDecimal) o.get("tailor_charges"), (BigDecimal) o.get("tax"), (BigDecimal) o.get("delivery_charge"), f.commissionPercent());
        List<Long> id = jdbc.queryForList("""
            INSERT INTO tailor_earnings (order_id, tailor_user_id, gross_amount, commission_rate, commission_amount, net_amount, payable_after)
            VALUES (?,?,?,?,?,?, now() + make_interval(days => ?)) ON CONFLICT (order_id) DO NOTHING RETURNING id""", Long.class,
            orderId, ((Number) o.get("tailor_id")).longValue(), (BigDecimal) o.get("tailor_charges"), f.commissionPercent(), sp.commission(), sp.tailorNet(), f.settlementHoldDays());
        if (id.isEmpty()) return;
        long ref = id.get(0);
        ledger.record(orderId, LedgerService.ITEM_REVENUE, sp.itemRevenue(), "DELIVERY", ref, "Items net of discount");
        ledger.record(orderId, LedgerService.PLATFORM_COMMISSION, sp.commission(), "EARNING", ref, "Commission " + f.commissionPercent() + "%");
        ledger.record(orderId, LedgerService.TAILOR_PAYABLE, sp.tailorNet(), "EARNING", ref, "Owed to the tailor");
        ledger.record(orderId, LedgerService.TAX_COLLECTED, sp.tax(), "DELIVERY", ref, "Tax collected");
        ledger.record(orderId, LedgerService.DELIVERY_REVENUE, sp.delivery(), "DELIVERY", ref, "Delivery charge");
    }

    // ---------------- tailor view ----------------

    public Summary summary(long tailorId) {
        Map<String, Object> m = jdbc.queryForMap("""
            SELECT COALESCE(SUM(net_amount) FILTER (WHERE status = 'ACCRUED' AND payable_after > now()), 0) AS pending,
                   COALESCE(SUM(net_amount) FILTER (WHERE status = 'ACCRUED' AND payable_after <= now()), 0) AS payable,
                   COALESCE(SUM(net_amount) FILTER (WHERE status = 'PAID'), 0) AS paid FROM tailor_earnings WHERE tailor_user_id = ?""", tailorId);
        return new Summary((BigDecimal) m.get("pending"), (BigDecimal) m.get("payable"), (BigDecimal) m.get("paid"), settings.load().commissionPercent());
    }

    public List<EarningRow> earnings(Long tailorId, String status, int limit) {
        StringBuilder sql = new StringBuilder("SELECT e.id, o.order_number, e.gross_amount, e.commission_rate, e.commission_amount, e.net_amount, e.status, e.accrued_at, e.payable_after, e.needs_recovery, (e.status = 'ACCRUED' AND e.payable_after <= now()) AS payable FROM tailor_earnings e JOIN orders o ON o.id = e.order_id WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (tailorId != null) { sql.append(" AND e.tailor_user_id = ?"); args.add(tailorId); }
        if (status != null && !status.isBlank()) { if (!Set.of("ACCRUED", "PAID", "REVERSED").contains(status)) throw bad("Unknown status."); sql.append(" AND e.status = ?"); args.add(status); }
        sql.append(" ORDER BY e.accrued_at DESC LIMIT ?"); args.add(Math.max(1, Math.min(limit, 200)));
        return jdbc.query(sql.toString(), (rs, i) -> new EarningRow(rs.getLong(1), rs.getString(2), rs.getBigDecimal(3), rs.getBigDecimal(4), rs.getBigDecimal(5), rs.getBigDecimal(6), rs.getString(7), Sql.ts(rs, "accrued_at"), Sql.ts(rs, "payable_after"), rs.getBoolean("needs_recovery"), rs.getBoolean("payable")), args.toArray());
    }

    public List<PayoutRow> payouts(Long tailorId) {
        return jdbc.query("SELECT p.id, t.full_name, p.amount, p.reference, p.note, a.full_name AS by_name, p.paid_at FROM tailor_payouts p JOIN users t ON t.id = p.tailor_user_id JOIN users a ON a.id = p.paid_by WHERE (?::bigint IS NULL OR p.tailor_user_id = ?) ORDER BY p.paid_at DESC LIMIT 200",
                (rs, i) -> new PayoutRow(rs.getLong(1), rs.getString(2), rs.getBigDecimal(3), rs.getString(4), rs.getString(5), rs.getString("by_name"), Sql.ts(rs, "paid_at")), tailorId, tailorId);
    }

    // ---------------- admin ----------------

    public List<TailorBalance> balances() {
        return jdbc.query("""
            SELECT e.tailor_user_id, u.full_name,
                   COALESCE(SUM(e.net_amount) FILTER (WHERE e.status = 'ACCRUED' AND e.payable_after > now()), 0) AS pending,
                   COALESCE(SUM(e.net_amount) FILTER (WHERE e.status = 'ACCRUED' AND e.payable_after <= now()), 0) AS payable,
                   count(*) FILTER (WHERE e.status = 'ACCRUED' AND e.payable_after <= now()) AS n, count(*) FILTER (WHERE e.needs_recovery) AS recovery
            FROM tailor_earnings e JOIN users u ON u.id = e.tailor_user_id GROUP BY e.tailor_user_id, u.full_name
            HAVING count(*) FILTER (WHERE e.status = 'ACCRUED' OR e.needs_recovery) > 0 ORDER BY payable DESC, u.full_name""",
            (rs, i) -> new TailorBalance(rs.getLong(1), rs.getString(2), rs.getBigDecimal("pending"), rs.getBigDecimal("payable"), rs.getInt("n"), rs.getInt("recovery")));
    }

    /** Pays out everything currently payable for one tailor, as one payout, after the admin has transferred the money. */
    public long recordPayout(long adminId, long tailorUserId, String reference, String note) {
        if (reference == null || reference.trim().length() < 3) throw bad("Enter the bank transfer reference.");
        if (reference.trim().length() > 100 || (note != null && note.length() > 300)) throw bad("Reference up to 100 characters, note up to 300.");
        long[] id = new long[1];
        tx.executeWithoutResult(s -> {
            List<Map<String, Object>> rows = jdbc.queryForList("SELECT id, net_amount FROM tailor_earnings WHERE tailor_user_id = ? AND status = 'ACCRUED' AND payable_after <= now() FOR UPDATE", tailorUserId);
            if (rows.isEmpty()) throw new BusinessException("NOTHING_PAYABLE", "This tailor has nothing payable right now.", HttpStatus.CONFLICT);
            BigDecimal total = rows.stream().map(r -> (BigDecimal) r.get("net_amount")).reduce(BigDecimal.ZERO, BigDecimal::add);
            id[0] = jdbc.queryForObject("INSERT INTO tailor_payouts (tailor_user_id, amount, reference, note, paid_by) VALUES (?,?,?,?,?) RETURNING id", Long.class, tailorUserId, total, reference.trim(), blank(note), adminId);
            for (Map<String, Object> r : rows) jdbc.update("UPDATE tailor_earnings SET status = 'PAID', payout_id = ? WHERE id = ?", id[0], r.get("id"));
            ledger.record(null, LedgerService.TAILOR_PAYABLE, total.negate(), "PAYOUT", id[0], "Payout " + reference.trim());
            notifications.notifyUser(tailorUserId, "PAYOUT_RECORDED", "Payout of ₹" + total + " recorded", "Reference " + reference.trim() + ". It covers " + rows.size() + " order(s).", "/tailor/earnings");
            audit.audit(adminId, "TAILOR_PAYOUT", "USER", tailorUserId, null, Map.of("amount", total, "orders", rows.size(), "reference", reference.trim()), note);
        });
        return id[0];
    }

    private static String blank(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}
