package com.clothingplatform.order;

import static com.clothingplatform.catalog.CatalogSupport.*;

import com.clothingplatform.address.AddressService;
import com.clothingplatform.cart.CartService;
import com.clothingplatform.cart.CartService.PricedItem;
import com.clothingplatform.common.PageResult;
import com.clothingplatform.common.Sql;
import com.clothingplatform.coupon.CouponService;
import com.clothingplatform.exception.BusinessException;
import com.clothingplatform.notification.EmailSender;
import com.clothingplatform.notification.NotificationService;
import com.clothingplatform.order.CheckoutPricingService.Computed;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

@Service
public class OrderService {
    public record CheckoutRequest(@NotNull Long addressId, @NotNull Long tailorCategoryId, String couponCode, @NotBlank @Pattern(regexp = "^(RAZORPAY|COD)$") String paymentMethod,
                                  @Size(max = 500) String deliveryInstructions) {}
    /** Published once an order is waiting for a tailor; the assignment module reacts after the transaction commits. */
    public record OrderReadyForAssignment(long orderId) {}
    public record PlacedOrder(long orderId, String orderNumber, BigDecimal total, String paymentMethod) {}

    public record OrderSummary(long id, String orderNumber, String status, String paymentMethod, String paymentStatus, BigDecimal total, int garmentCount, String firstItemName, String firstItemImage, Instant placedAt, String customerName) {}
    public record OrderItemView(long id, long productId, String name, String slug, String imageUrl, int quantity, BigDecimal unitPrice, BigDecimal lineTotal, String sizeCode, String measurementProfile, JsonNode measurements, JsonNode details) {}
    public record TimelineEntry(String status, String note, Instant at, List<String> photos) {}
    public record PaymentInfo(long id, String method, String status, BigDecimal amount, String instrument, String failureReason, Instant paidAt) {}
    public record OrderDetail(long id, String orderNumber, String status, String paymentMethod, String paymentStatus, String tailorCategory, int garmentCount, BigDecimal subtotal, BigDecimal discount,
                              String couponCode, BigDecimal tailorCharges, BigDecimal taxRatePercent, BigDecimal tax, BigDecimal deliveryCharge, BigDecimal total, String currency, JsonNode shippingAddress,
                              String customerName, String customerPhone, String customerEmail, String deliveryInstructions, Instant placedAt, List<OrderItemView> items, List<TimelineEntry> timeline,
                              List<PaymentInfo> payments, String invoiceNumber, String assignedTailor) {}

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final AddressService addresses;
    private final CheckoutPricingService pricing;
    private final CartService cart;
    private final CouponService coupons;
    private final NotificationService notifications;
    private final EmailSender email;
    private final ApplicationEventPublisher events;
    public OrderService(JdbcTemplate jdbc, ObjectMapper mapper, AddressService addresses, CheckoutPricingService pricing, CartService cart, CouponService coupons, NotificationService notifications, EmailSender email, ApplicationEventPublisher events) {
        this.events = events;
        this.jdbc = jdbc; this.mapper = mapper; this.addresses = addresses; this.pricing = pricing; this.cart = cart; this.coupons = coupons; this.notifications = notifications; this.email = email;
    }

    // ---------------- placement ----------------

    /** Creates the order from the server-priced cart in one transaction; the browser supplies choices, never amounts. */
    @Transactional
    public PlacedOrder place(long userId, CheckoutRequest r) {
        var address = addresses.require(userId, r.addressId());
        Computed c = pricing.compute(userId, r.tailorCategoryId(), r.couponCode(), true);
        if (!c.errors().isEmpty()) throw bad(c.errors().get(0));
        if ("COD".equals(r.paymentMethod()) && !c.settings().codEnabled()) throw bad("Cash on delivery is not available right now.");

        String number = jdbc.queryForObject("SELECT 'ORD-' || to_char(now(), 'YYMMDD') || '-' || nextval('order_number_seq')", String.class);
        var t = c.totals();
        long orderId = jdbc.queryForObject("""
            INSERT INTO orders (order_number, user_id, status, payment_method, payment_status, tailor_category_id, tailor_category_name, tailor_price_per_garment, garment_count,
                subtotal, discount, coupon_code, tailor_charges, tax_rate_percent, tax, delivery_charge, total, shipping_address, customer_name, customer_phone, delivery_instructions)
            VALUES (?, ?, 'ORDER_PLACED', ?, 'PENDING', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?) RETURNING id""", Long.class,
            number, userId, r.paymentMethod(), c.tailor().id(), c.tailor().name(), c.tailor().stitchingPrice(), c.garments(), t.subtotal(), t.discount(),
            c.coupon() == null ? null : c.coupon().code(), t.tailorCharges(), t.taxRatePercent(), t.tax(), t.delivery(), t.total(), json(address), address.fullName(), address.phone(), r.deliveryInstructions());
        history(orderId, "ORDER_PLACED", "Order placed", userId);

        for (PricedItem i : c.items()) insertItem(orderId, userId, i);
        if (c.coupon() != null) coupons.redeem(c.coupon(), userId, orderId);
        cart.clear(userId);

        if ("COD".equals(r.paymentMethod())) {
            jdbc.update("INSERT INTO payments (order_id, user_id, method, status, amount) VALUES (?,?,'COD','PENDING_COLLECTION',?)", orderId, userId, t.total());
            jdbc.update("UPDATE orders SET payment_status = 'PENDING_COLLECTION' WHERE id = ?", orderId);
            transition(orderId, OrderStatus.TAILOR_ASSIGNMENT_PENDING, "Cash on delivery. Waiting for a tailor", null);
            ensureInvoice(orderId);
            notifyPlaced(userId, orderId, number, t.total(), true);
            events.publishEvent(new OrderReadyForAssignment(orderId));
        } else {
            transition(orderId, OrderStatus.PAYMENT_PENDING, "Waiting for online payment", null);
        }
        return new PlacedOrder(orderId, number, t.total(), r.paymentMethod());
    }

    private void insertItem(long orderId, long userId, PricedItem i) {
        String profileName = null, measurements = null;
        if (i.configuration().measurementProfileId() != null) {
            var m = jdbc.queryForList("SELECT name, measurements::text AS m FROM measurement_profiles WHERE id = ? AND user_id = ?", i.configuration().measurementProfileId(), userId);
            if (!m.isEmpty()) { profileName = (String) m.get(0).get("name"); measurements = (String) m.get(0).get("m"); }
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("fabric", i.details().fabric()); details.put("color", i.details().color());
        details.put("selections", i.details().selections()); details.put("priceLines", i.lines());
        jdbc.update("""
            INSERT INTO order_items (order_id, product_id, product_name, product_slug, image_url, quantity, unit_price, line_total, size_code, measurements, measurement_profile_name, configuration, details)
            VALUES (?,?,?,?,?,?,?,?,?,?::jsonb,?,?::jsonb,?::jsonb)""", orderId, i.productId(), i.name(), i.slug(), i.imageUrl(), i.quantity(), i.unitPrice(), i.lineTotal(),
            i.configuration().sizeCode(), measurements, profileName, json(i.configuration()), json(details));
    }

    public void transition(long orderId, OrderStatus to, String note, Long actorId) { transition(orderId, to, note, actorId, null); }

    /** The single place order status changes. Invalid jumps are refused with 409 and nothing is written. Photos (server-stored urls) appear on the timeline. */
    @Transactional
    public void transition(long orderId, OrderStatus to, String note, Long actorId, List<String> photos) {
        String cur = jdbc.query("SELECT status FROM orders WHERE id = ? FOR UPDATE", rs -> rs.next() ? rs.getString(1) : null, orderId);
        if (cur == null) throw notFound("Order");
        OrderStatus from = OrderStatus.valueOf(cur);
        if (!OrderStatusMachine.allowed(from, to)) throw new BusinessException("INVALID_TRANSITION", "An order cannot move from " + from + " to " + to + ".", HttpStatus.CONFLICT);
        jdbc.update("UPDATE orders SET status = ?, updated_at = now() WHERE id = ?", to.name(), orderId);
        jdbc.update("INSERT INTO order_status_history (order_id, status, note, actor_id, photos) VALUES (?,?,?,?,?::jsonb)", orderId, to.name(), note, actorId, photos == null || photos.isEmpty() ? null : json(photos));
    }

    /** An online order whose payment could not even be started is cancelled and its coupon use returned. */
    @Transactional
    public void cancelUnpaid(long orderId, String reason) {
        transition(orderId, OrderStatus.CANCELLED, reason, null);
        coupons.release(orderId);
    }

    private void history(long orderId, String status, String note, Long actor) {
        jdbc.update("INSERT INTO order_status_history (order_id, status, note, actor_id) VALUES (?,?,?,?)", orderId, status, note, actor);
    }

    @Transactional
    public void ensureInvoice(long orderId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM invoices WHERE order_id = ?", Integer.class, orderId);
        if (n != null && n > 0) return;
        jdbc.update("INSERT INTO invoices (order_id, invoice_number) VALUES (?, 'INV-' || to_char(now(), 'YYYY') || '-' || lpad(nextval('invoice_number_seq')::text, 6, '0')) ON CONFLICT (order_id) DO NOTHING", orderId);
    }

    public void notifyPlaced(long userId, long orderId, String number, BigDecimal total, boolean cod) {
        notifications.notifyUser(userId, "ORDER_PLACED", "Order " + number + " placed", cod ? "Pay ₹" + total + " in cash on delivery. We are finding a tailor." : "Payment received. We are finding a tailor.", "/account/orders/" + orderId);
        notifications.notifyPermissionHolders("ORDER_VIEW", "ORDER_PLACED_ADMIN", "New order " + number, "₹" + total + (cod ? " (cash on delivery)" : " (paid online)"), "/admin/orders/" + orderId);
        String to = jdbc.queryForObject("SELECT email FROM users WHERE id = ?", String.class, userId);
        email.send(to, "Order " + number + " confirmed", "Thank you. Your order " + number + " (₹" + total + ") has been placed" + (cod ? " with cash on delivery" : " and paid") + ". You can follow it under My orders.");
    }

    // ---------------- queries ----------------

    private static final String SUMMARY = """
        SELECT o.id, o.order_number, o.status, o.payment_method, o.payment_status, o.total, o.garment_count, o.placed_at, o.customer_name,
               (SELECT product_name FROM order_items i WHERE i.order_id = o.id ORDER BY i.id LIMIT 1) AS first_name,
               (SELECT image_url FROM order_items i WHERE i.order_id = o.id ORDER BY i.id LIMIT 1) AS first_image
        FROM orders o WHERE 1 = 1""";

    private static OrderSummary summary(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new OrderSummary(rs.getLong("id"), rs.getString("order_number"), rs.getString("status"), rs.getString("payment_method"), rs.getString("payment_status"), rs.getBigDecimal("total"),
                rs.getInt("garment_count"), rs.getString("first_name"), rs.getString("first_image"), Sql.ts(rs, "placed_at"), rs.getString("customer_name"));
    }

    public PageResult<OrderSummary> listForUser(long userId, int page, int size) {
        int p = Sql.clampPage(page), s = Sql.clampSize(size);
        Long total = jdbc.queryForObject("SELECT count(*) FROM orders WHERE user_id = ?", Long.class, userId);
        List<OrderSummary> items = jdbc.query(SUMMARY + " AND o.user_id = ? ORDER BY o.placed_at DESC LIMIT ? OFFSET ?", (rs, i) -> summary(rs), userId, s, p * s);
        return new PageResult<>(items, total == null ? 0 : total, p, s);
    }

    public PageResult<OrderSummary> adminList(String status, String q, int page, int size) {
        StringBuilder where = new StringBuilder(); List<Object> args = new ArrayList<>();
        if (status != null && !status.isBlank()) { try { OrderStatus.valueOf(status); } catch (IllegalArgumentException e) { throw bad("Unknown status."); } where.append(" AND o.status = ?"); args.add(status); }
        if (q != null && !q.isBlank()) { where.append(" AND (o.order_number ILIKE ? OR o.customer_name ILIKE ? OR o.customer_phone ILIKE ?)"); String like = "%" + q.trim() + "%"; args.add(like); args.add(like); args.add(like); }
        int p = Sql.clampPage(page), s = Sql.clampSize(size);
        Long total = jdbc.queryForObject("SELECT count(*) FROM orders o WHERE 1 = 1" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args); pageArgs.add(s); pageArgs.add(p * s);
        List<OrderSummary> items = jdbc.query(SUMMARY + where + " ORDER BY o.placed_at DESC LIMIT ? OFFSET ?", (rs, i) -> summary(rs), pageArgs.toArray());
        return new PageResult<>(items, total == null ? 0 : total, p, s);
    }

    /** @param userId the owner when called by a customer (another customer's order is a 404); null for admins */
    public OrderDetail detail(Long userId, long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT o.*, o.shipping_address::text AS addr, u.email, (SELECT invoice_number FROM invoices v WHERE v.order_id = o.id) AS inv,
                   (SELECT split_part(t.full_name, ' ', 1) FROM users t WHERE t.id = o.tailor_id) AS tailor_name
            FROM orders o JOIN users u ON u.id = o.user_id WHERE o.id = ?""" + (userId == null ? "" : " AND o.user_id = ?"), userId == null ? new Object[]{id} : new Object[]{id, userId});
        if (rows.isEmpty()) throw notFound("Order");
        Map<String, Object> o = rows.get(0);
        List<OrderItemView> items = jdbc.query("""
            SELECT id, product_id, product_name, product_slug, image_url, quantity, unit_price, line_total, size_code, measurement_profile_name, measurements::text AS m, details::text AS d
            FROM order_items WHERE order_id = ? ORDER BY id""", (rs, i) -> new OrderItemView(rs.getLong("id"), rs.getLong("product_id"), rs.getString("product_name"), rs.getString("product_slug"), rs.getString("image_url"),
                rs.getInt("quantity"), rs.getBigDecimal("unit_price"), rs.getBigDecimal("line_total"), rs.getString("size_code"), rs.getString("measurement_profile_name"), parse(rs.getString("m")), parse(rs.getString("d"))), id);
        List<TimelineEntry> timeline = jdbc.query("SELECT status, note, created_at, photos::text AS p FROM order_status_history WHERE order_id = ? ORDER BY created_at, id",
                (rs, i) -> new TimelineEntry(rs.getString(1), rs.getString(2), Sql.ts(rs, "created_at"), photoList(rs.getString("p"))), id);
        List<PaymentInfo> payments = jdbc.query("SELECT id, method, status, amount, payment_instrument, failure_reason, paid_at FROM payments WHERE order_id = ? ORDER BY id",
                (rs, i) -> new PaymentInfo(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getBigDecimal(4), rs.getString(5), rs.getString(6), Sql.ts(rs, "paid_at")), id);
        return new OrderDetail(((Number) o.get("id")).longValue(), (String) o.get("order_number"), (String) o.get("status"), (String) o.get("payment_method"), (String) o.get("payment_status"),
                (String) o.get("tailor_category_name"), ((Number) o.get("garment_count")).intValue(), (BigDecimal) o.get("subtotal"), (BigDecimal) o.get("discount"), (String) o.get("coupon_code"),
                (BigDecimal) o.get("tailor_charges"), (BigDecimal) o.get("tax_rate_percent"), (BigDecimal) o.get("tax"), (BigDecimal) o.get("delivery_charge"), (BigDecimal) o.get("total"), (String) o.get("currency"),
                parse((String) o.get("addr")), (String) o.get("customer_name"), (String) o.get("customer_phone"), (String) o.get("email"), (String) o.get("delivery_instructions"),
                ((java.sql.Timestamp) o.get("placed_at")).toInstant(), items, timeline, payments, (String) o.get("inv"), (String) o.get("tailor_name"));
    }

    /** A printable invoice page (save as PDF from the browser). Exists only once the order is confirmed or placed as cash on delivery. */
    public String invoiceHtml(Long userId, long orderId, String shopName) {
        OrderDetail d = detail(userId, orderId);
        if (d.invoiceNumber() == null) throw new BusinessException("INVOICE_NOT_READY", "The invoice is created once the order is confirmed.", HttpStatus.CONFLICT);
        String h = HtmlUtils.htmlEscape(shopName);
        StringBuilder b = new StringBuilder("<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\"><title>Invoice " + HtmlUtils.htmlEscape(d.invoiceNumber()) + "</title><style>")
            .append("body{font-family:Arial,sans-serif;color:#222;max-width:760px;margin:24px auto;padding:0 16px}table{width:100%;border-collapse:collapse}th,td{padding:8px;border-bottom:1px solid #ddd;text-align:left;vertical-align:top}")
            .append("td.r,th.r{text-align:right}h1{margin:0}.muted{color:#666;font-size:13px}.tot td{border:0}.tot .b{font-weight:bold;font-size:1.1em}@media print{button{display:none}}</style></head><body>")
            .append("<button onclick=\"window.print()\">Print or save as PDF</button><h1>").append(h).append("</h1><p class=\"muted\">Tax invoice</p>")
            .append("<p><strong>Invoice ").append(HtmlUtils.htmlEscape(d.invoiceNumber())).append("</strong><br>Order ").append(HtmlUtils.htmlEscape(d.orderNumber())).append("<br>Date ").append(d.placedAt()).append("</p>")
            .append("<p><strong>Billed and shipped to</strong><br>").append(HtmlUtils.htmlEscape(d.customerName())).append("<br>").append(addressLines(d.shippingAddress())).append("<br>").append(HtmlUtils.htmlEscape(d.customerPhone())).append("</p>")
            .append("<table><thead><tr><th>Item</th><th class=\"r\">Qty</th><th class=\"r\">Unit</th><th class=\"r\">Amount</th></tr></thead><tbody>");
        for (OrderItemView i : d.items()) {
            b.append("<tr><td>").append(HtmlUtils.htmlEscape(i.name()));
            JsonNode det = i.details();
            if (det != null) {
                StringBuilder opts = new StringBuilder();
                if (det.hasNonNull("fabric")) opts.append(det.get("fabric").asText());
                if (det.hasNonNull("color")) opts.append(opts.length() > 0 ? ", " : "").append(det.get("color").asText());
                for (JsonNode s : det.path("selections")) opts.append(opts.length() > 0 ? ", " : "").append(s.path("group").asText()).append(": ").append(s.path("option").asText());
                if (opts.length() > 0) b.append("<div class=\"muted\">").append(HtmlUtils.htmlEscape(opts.toString())).append("</div>");
            }
            b.append("</td><td class=\"r\">").append(i.quantity()).append("</td><td class=\"r\">").append(money(i.unitPrice())).append("</td><td class=\"r\">").append(money(i.lineTotal())).append("</td></tr>");
        }
        b.append("</tbody></table><table class=\"tot\" style=\"margin-top:12px\"><tbody>")
            .append(row("Items", d.subtotal()));
        if (d.discount().signum() > 0) b.append(row("Discount" + (d.couponCode() != null ? " (" + HtmlUtils.htmlEscape(d.couponCode()) + ")" : ""), d.discount().negate()));
        b.append(row("Stitching (" + HtmlUtils.htmlEscape(d.tailorCategory()) + " tailor × " + d.garmentCount() + ")", d.tailorCharges()))
            .append(row("Tax (" + d.taxRatePercent().stripTrailingZeros().toPlainString() + "%)", d.tax())).append(row("Delivery", d.deliveryCharge()))
            .append("<tr class=\"b\"><td class=\"r b\">Total</td><td class=\"r b\" style=\"width:120px\">").append(money(d.total())).append("</td></tr></tbody></table>")
            .append("<p class=\"muted\">Payment: ").append(d.paymentMethod().equals("COD") ? "Cash on delivery" : "Online").append(" · ").append(HtmlUtils.htmlEscape(d.paymentStatus().replace('_', ' ').toLowerCase())).append("</p>")
            .append("<p class=\"muted\">This is a computer-generated invoice.</p></body></html>");
        return b.toString();
    }

    private static String row(String label, BigDecimal v) { return "<tr><td class=\"r\">" + label + "</td><td class=\"r\" style=\"width:120px\">" + money(v) + "</td></tr>"; }
    private static String money(BigDecimal v) { return "₹" + v.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(); }
    private static String addressLines(JsonNode a) {
        if (a == null) return "";
        List<String> parts = new ArrayList<>();
        for (String k : List.of("line1", "line2", "city", "state", "postalCode")) if (a.hasNonNull(k) && !a.get(k).asText().isBlank()) parts.add(HtmlUtils.htmlEscape(a.get(k).asText()));
        return String.join(", ", parts);
    }

    private List<String> photoList(String s) {
        JsonNode n = parse(s);
        if (n == null || !n.isArray()) return List.of();
        List<String> out = new ArrayList<>(); n.forEach(x -> out.add(x.asText())); return out;
    }
    private String json(Object o) { try { return mapper.writeValueAsString(o); } catch (JsonProcessingException e) { throw bad("Invalid data."); } }
    private JsonNode parse(String s) { if (s == null) return null; try { return mapper.readTree(s); } catch (JsonProcessingException e) { return null; } }
}
