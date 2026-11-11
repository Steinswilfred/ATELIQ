package com.clothingplatform.finance;

import com.clothingplatform.settings.SettingsService;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Business rules the owner can change under Website settings. Defaults apply if a value is missing. */
public record FinanceSettings(BigDecimal commissionPercent, int settlementHoldDays, BigDecimal lateCancellationFeePercent, boolean autoApproveCustomerRefunds) {

    @Service
    public static class Loader {
        private final SettingsService settings;
        public Loader(SettingsService settings) { this.settings = settings; }

        public FinanceSettings load() {
            Map<String, JsonNode> s = settings.allSettings();
            return new FinanceSettings(num(s, "finance.commissionPercent", "15"), num(s, "finance.settlementHoldDays", "7").intValue(), num(s, "finance.lateCancellationFeePercent", "10"),
                    !s.containsKey("finance.autoApproveCustomerRefunds") || s.get("finance.autoApproveCustomerRefunds").asBoolean(true));
        }
        private static BigDecimal num(Map<String, JsonNode> s, String key, String dflt) { JsonNode n = s.get(key); return n != null && n.isNumber() ? n.decimalValue() : new BigDecimal(dflt); }
    }
}
