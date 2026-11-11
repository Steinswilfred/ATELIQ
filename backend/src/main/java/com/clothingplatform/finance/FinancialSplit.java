package com.clothingplatform.finance;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * How one delivered order's total divides into separate kinds of money. The five parts always add up to the order total:
 * item revenue (items less discount) + platform commission + tailor's net + tax collected + delivery charge.
 * Commission applies to the stitching charge only; the tailor keeps the rest of it.
 */
public final class FinancialSplit {
    private FinancialSplit() {}

    public record Split(BigDecimal itemRevenue, BigDecimal commission, BigDecimal tailorNet, BigDecimal tax, BigDecimal delivery) {
        public BigDecimal total() { return itemRevenue.add(commission).add(tailorNet).add(tax).add(delivery); }
    }

    private static BigDecimal r(BigDecimal v) { return v.setScale(2, RoundingMode.HALF_UP); }

    public static Split compute(BigDecimal subtotal, BigDecimal discount, BigDecimal tailorCharges, BigDecimal tax, BigDecimal delivery, BigDecimal commissionPercent) {
        BigDecimal charge = r(tailorCharges);
        BigDecimal commission = r(charge.multiply(commissionPercent).divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP));
        return new Split(r(subtotal.subtract(discount)), commission, charge.subtract(commission), r(tax), r(delivery));
    }
}
