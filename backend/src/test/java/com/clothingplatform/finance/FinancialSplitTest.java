package com.clothingplatform.finance;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class FinancialSplitTest {
    private static BigDecimal d(String s) { return new BigDecimal(s); }

    @Test
    void partsAddUpToTheOrderTotalAndCommissionIsOnStitchingOnly() {
        // the order from PricingCalculatorTest: items 2000, coupon 200, stitching 1000, tax 140, delivery 99 => total 3039
        var s = FinancialSplit.compute(d("2000"), d("200"), d("1000"), d("140"), d("99"), d("15"));
        assertEquals(0, d("150.00").compareTo(s.commission()));      // 15% of 1000
        assertEquals(0, d("850.00").compareTo(s.tailorNet()));
        assertEquals(0, d("1800.00").compareTo(s.itemRevenue()));
        assertEquals(0, d("3039.00").compareTo(s.total()));
    }

    @Test
    void roundingNeverLosesOrInventsMoney() {
        var s = FinancialSplit.compute(d("999.99"), d("0"), d("333.33"), d("66.67"), d("99"), d("12.5"));
        assertEquals(0, d("41.67").compareTo(s.commission()));       // 12.5% of 333.33 = 41.66625
        assertEquals(0, s.commission().add(s.tailorNet()).compareTo(d("333.33")));
        assertEquals(0, d("1498.99").compareTo(s.total()));
    }

    @Test
    void zeroCommissionGivesTheTailorEverything() {
        var s = FinancialSplit.compute(d("1000"), d("0"), d("500"), d("0"), d("0"), d("0"));
        assertEquals(0, s.commission().signum());
        assertEquals(0, d("500.00").compareTo(s.tailorNet()));
    }
}
