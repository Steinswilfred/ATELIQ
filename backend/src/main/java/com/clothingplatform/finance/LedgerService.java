package com.clothingplatform.finance;

import java.math.BigDecimal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Append-only money record. Entries are only ever added, never edited or deleted; a correction is a new, opposite entry. */
@Service
public class LedgerService {
    public static final String CUSTOMER_PAYMENTS = "CUSTOMER_PAYMENTS", ITEM_REVENUE = "ITEM_REVENUE", DELIVERY_REVENUE = "DELIVERY_REVENUE", PLATFORM_COMMISSION = "PLATFORM_COMMISSION",
            TAX_COLLECTED = "TAX_COLLECTED", TAILOR_PAYABLE = "TAILOR_PAYABLE", REVENUE_REFUNDED = "REVENUE_REFUNDED";

    private final JdbcTemplate jdbc;
    public LedgerService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void record(Long orderId, String account, BigDecimal amount, String refType, Long refId, String note) {
        if (amount.signum() == 0) return;
        jdbc.update("INSERT INTO ledger_entries (order_id, account, amount, ref_type, ref_id, note) VALUES (?,?,?,?,?,?)", orderId, account, amount, refType, refId, note);
    }
}
