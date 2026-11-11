package com.clothingplatform.order;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Pure money arithmetic for checkout. All figures are rupees with two decimals, rounded half-up. */
public final class PricingCalculator {
    private PricingCalculator() {}

    public record Totals(BigDecimal subtotal, BigDecimal discount, BigDecimal tailorCharges, BigDecimal taxRatePercent, BigDecimal tax, BigDecimal delivery, BigDecimal total) {}

    private static BigDecimal r(BigDecimal v) { return v.setScale(2, RoundingMode.HALF_UP); }

    /**
     * Tax applies to (items - discount + tailor charges); delivery is added afterwards and is not taxed in this model.
     * Delivery is free when freeDeliveryAbove > 0 and (items - discount) reaches it.
     */
    public static Totals compute(BigDecimal subtotal, BigDecimal discount, int garments, BigDecimal tailorPrice, BigDecimal taxRatePercent, BigDecimal deliveryCharge, BigDecimal freeDeliveryAbove) {
        BigDecimal sub = r(subtotal);
        BigDecimal disc = r(discount.min(sub).max(BigDecimal.ZERO));
        BigDecimal tailor = r(tailorPrice.multiply(BigDecimal.valueOf(garments)));
        BigDecimal afterDiscount = sub.subtract(disc);
        BigDecimal taxable = afterDiscount.add(tailor);
        BigDecimal tax = r(taxable.multiply(taxRatePercent).divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP));
        boolean free = freeDeliveryAbove.signum() > 0 && afterDiscount.compareTo(freeDeliveryAbove) >= 0;
        BigDecimal delivery = free ? BigDecimal.ZERO.setScale(2) : r(deliveryCharge);
        return new Totals(sub, disc, tailor, taxRatePercent, tax, delivery, r(taxable.add(tax).add(delivery)));
    }

    /** Coupon value for a subtotal: percentage (optionally capped) or a fixed amount, never more than the subtotal. */
    public static BigDecimal couponDiscount(String type, BigDecimal value, BigDecimal maxDiscount, BigDecimal subtotal) {
        BigDecimal d = "PERCENT".equals(type) ? r(subtotal.multiply(value).divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP)) : r(value);
        if ("PERCENT".equals(type) && maxDiscount != null) d = d.min(r(maxDiscount));
        return d.min(r(subtotal));
    }

    public static long toPaise(BigDecimal rupees) { return rupees.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact(); }
}
