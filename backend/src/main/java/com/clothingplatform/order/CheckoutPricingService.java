package com.clothingplatform.order;

import com.clothingplatform.cart.CartService;
import com.clothingplatform.cart.CartService.PricedItem;
import com.clothingplatform.coupon.CouponService;
import com.clothingplatform.exception.BusinessException;
import com.clothingplatform.order.PricingCalculator.Totals;
import com.clothingplatform.shop.ShopDtos.TailorCategoryPublic;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** One calculation used by both the checkout preview and the real order, so what the customer sees is what is charged. */
@Service
public class CheckoutPricingService {
    public record Computed(List<PricedItem> items, int garments, TailorCategoryPublic tailor, CouponService.Applied coupon, Totals totals, List<String> errors, CheckoutSettings settings) {}

    private final CartService cart;
    private final CouponService coupons;
    private final CheckoutSettings.Loader settingsLoader;
    private final JdbcTemplate jdbc;
    public CheckoutPricingService(CartService cart, CouponService coupons, CheckoutSettings.Loader settingsLoader, JdbcTemplate jdbc) { this.cart = cart; this.coupons = coupons; this.settingsLoader = settingsLoader; this.jdbc = jdbc; }

    /** @param placing true when creating an order: a tailor category is mandatory and the coupon row is locked */
    public Computed compute(long userId, Long tailorCategoryId, String couponCode, boolean placing) {
        List<String> errors = new ArrayList<>();
        List<PricedItem> items = cart.priceItems(userId);
        if (items.isEmpty()) errors.add("Your cart is empty.");
        for (PricedItem i : items) for (String p : i.problems()) errors.add(i.name() + ": " + p);
        int garments = items.stream().filter(i -> i.available() && i.problems().isEmpty()).mapToInt(PricedItem::quantity).sum();
        BigDecimal subtotal = items.stream().filter(i -> i.available() && i.problems().isEmpty()).map(PricedItem::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);

        TailorCategoryPublic tailor = null;
        if (tailorCategoryId != null) {
            List<TailorCategoryPublic> l = jdbc.query("SELECT id, name, description, level, stitching_price FROM tailor_categories WHERE id = ? AND deleted_at IS NULL AND is_active",
                    (rs, i) -> new TailorCategoryPublic(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getInt(4), rs.getBigDecimal(5)), tailorCategoryId);
            if (l.isEmpty()) errors.add("That tailor category is not available."); else tailor = l.get(0);
        } else if (placing) errors.add("Choose a tailor category.");

        CouponService.Applied coupon = null;
        if (couponCode != null && !couponCode.isBlank()) {
            try { coupon = coupons.apply(couponCode, userId, subtotal, placing); }
            catch (BusinessException e) { errors.add(e.getMessage()); }
        }
        CheckoutSettings s = settingsLoader.load();
        Totals totals = PricingCalculator.compute(subtotal, coupon == null ? BigDecimal.ZERO : coupon.discount(), garments, tailor == null ? BigDecimal.ZERO : tailor.stitchingPrice(),
                s.taxRatePercent(), s.deliveryCharge(), s.freeDeliveryAbove());
        return new Computed(items, garments, tailor, coupon, totals, errors, s);
    }
}
