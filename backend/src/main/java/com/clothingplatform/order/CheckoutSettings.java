package com.clothingplatform.order;

import com.clothingplatform.settings.SettingsService;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.stereotype.Service;

public record CheckoutSettings(BigDecimal taxRatePercent, BigDecimal deliveryCharge, BigDecimal freeDeliveryAbove, boolean codEnabled) {

    @Service
    public static class Loader {
        private final SettingsService settings;
        public Loader(SettingsService settings) { this.settings = settings; }

        public CheckoutSettings load() {
            Map<String, JsonNode> s = settings.publicSettings();
            return new CheckoutSettings(num(s, "checkout.taxRatePercent", "5"), num(s, "checkout.deliveryCharge", "99"), num(s, "checkout.freeDeliveryAbove", "0"),
                    !s.containsKey("checkout.codEnabled") || s.get("checkout.codEnabled").asBoolean(true));
        }
        private static BigDecimal num(Map<String, JsonNode> s, String key, String dflt) {
            JsonNode n = s.get(key);
            return n != null && n.isNumber() ? n.decimalValue() : new BigDecimal(dflt);
        }
    }
}
