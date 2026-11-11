package com.clothingplatform.shop;

import static org.junit.jupiter.api.Assertions.*;

import com.clothingplatform.shop.ShopDtos.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The quote engine is pure logic, so it runs without a database. */
class DesignServiceQuoteTest {
    private final DesignService service = new DesignService(null, null, new ObjectMapper());

    private ProductPublic shirt() {
        var white = new ColorPublic(1, "White", "#FFFFFF");
        var cotton = new FabricPublic(10, "Cotton", "100% cotton", null, new BigDecimal("200.00"), null, List.of(white));
        var spread = new OptionPublic(100, "spread", "Spread", null, null, new BigDecimal("50.00"), true, null);
        var button = new OptionPublic(101, "button_down", "Button-down", null, null, BigDecimal.ZERO, false, null);
        var collar = new GroupPublic(5, "collar", "Collar", null, true, List.of(spread, button));
        return new ProductPublic(1, "Shirt", "shirt", null, new BigDecimal("1000.00"), "Shirts", "Men", null, List.of(), List.of(new SizePublic("M", "Medium")), List.of(cotton), List.of(collar));
    }

    @Test
    void pricesBasePlusFabricPlusOptions() {
        Quote q = service.quote(shirt(), new DesignConfig("M", null, 10L, 1L, Map.of(5L, 100L)), true);
        assertTrue(q.valid(), q.errors().toString());
        assertEquals(0, new BigDecimal("1250.00").compareTo(q.total()));
    }

    @Test
    void rejectsMissingRequiredChoicesAndUnknownIds() {
        Quote q = service.quote(shirt(), new DesignConfig(null, null, null, null, Map.of(99L, 1L)), true);
        assertFalse(q.valid());
        assertTrue(q.errors().size() >= 3);
    }

    @Test
    void rejectsColourFromAnotherFabricAndBothFitModes() {
        Quote q = service.quote(shirt(), new DesignConfig("M", 7L, 10L, 2L, Map.of(5L, 100L)), true);
        assertFalse(q.valid());
        assertTrue(q.errors().stream().anyMatch(e -> e.contains("colour")));
        assertTrue(q.errors().stream().anyMatch(e -> e.contains("not both")));
    }

    @Test
    void quoteWithoutFitIsAllowedWhileBrowsing() {
        assertTrue(service.quote(shirt(), new DesignConfig(null, null, 10L, 1L, Map.of(5L, 101L)), false).valid());
    }

    @Test
    void addsTheAdminSetTailorChargeAndRejectsUnknownCategory() {
        var master = new TailorCategoryPublic(5, "Master", null, 5, new BigDecimal("2000.00"));
        var cfg = new DesignConfig("M", null, 10L, 1L, Map.of(5L, 100L), 5L);
        Quote q = service.quoteWith(shirt(), cfg, true, master);
        assertTrue(q.valid(), q.errors().toString());
        assertEquals(0, new BigDecimal("3250.00").compareTo(q.total()));
        assertFalse(service.quoteWith(shirt(), cfg, true, null).valid());
    }
}
