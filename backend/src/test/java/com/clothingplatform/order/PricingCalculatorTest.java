package com.clothingplatform.order;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PricingCalculatorTest {
    private static BigDecimal d(String s) { return new BigDecimal(s); }

    @Test
    void totalsWithTailorTaxAndDelivery() {
        // items 2000, coupon 200, 2 garments x 500 tailor, 5% tax, delivery 99
        var t = PricingCalculator.compute(d("2000"), d("200"), 2, d("500"), d("5"), d("99"), d("0"));
        assertEquals(0, d("1000.00").compareTo(t.tailorCharges()));
        assertEquals(0, d("140.00").compareTo(t.tax()));            // (1800 + 1000) * 5%
        assertEquals(0, d("99.00").compareTo(t.delivery()));
        assertEquals(0, d("3039.00").compareTo(t.total()));          // 2800 + 140 + 99
    }

    @Test
    void freeDeliveryAboveThresholdOnly() {
        assertEquals(0, PricingCalculator.compute(d("5000"), d("0"), 1, d("0"), d("0"), d("99"), d("5000")).delivery().signum());
        assertEquals(0, d("99.00").compareTo(PricingCalculator.compute(d("4999"), d("0"), 1, d("0"), d("0"), d("99"), d("5000")).delivery()));
        assertEquals(0, d("99.00").compareTo(PricingCalculator.compute(d("9000"), d("0"), 1, d("0"), d("0"), d("99"), d("0")).delivery()));   // 0 = never free
    }

    @Test
    void discountNeverExceedsSubtotal() {
        var t = PricingCalculator.compute(d("100"), d("500"), 1, d("0"), d("0"), d("0"), d("0"));
        assertEquals(0, d("100.00").compareTo(t.discount()));
        assertEquals(0, t.total().signum());
    }

    @Test
    void couponRules() {
        assertEquals(0, d("150.00").compareTo(PricingCalculator.couponDiscount("PERCENT", d("10"), d("150"), d("2000"))));   // 10% = 200, capped at 150
        assertEquals(0, d("200.00").compareTo(PricingCalculator.couponDiscount("PERCENT", d("10"), null, d("2000"))));
        assertEquals(0, d("300.00").compareTo(PricingCalculator.couponDiscount("FIXED", d("300"), null, d("2000"))));
        assertEquals(0, d("250.00").compareTo(PricingCalculator.couponDiscount("FIXED", d("300"), null, d("250"))));
    }

    @Test
    void paiseConversionIsExact() {
        assertEquals(303900L, PricingCalculator.toPaise(d("3039.00")));
        assertEquals(1L, PricingCalculator.toPaise(d("0.01")));
    }
}
