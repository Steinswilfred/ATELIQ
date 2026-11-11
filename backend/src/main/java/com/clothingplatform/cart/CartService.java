package com.clothingplatform.cart;

import static com.clothingplatform.catalog.CatalogSupport.*;

import com.clothingplatform.exception.BusinessException;
import com.clothingplatform.shop.DesignService;
import com.clothingplatform.shop.PublicCatalogService;
import com.clothingplatform.shop.ShopDtos.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The cart stores choices only. Every price shown or charged is recomputed here from current database prices. */
@Service
public class CartService {
    public record Selection(String group, String option) {}
    public record ItemDetails(String fabric, String color, List<Selection> selections) {}
    public record PricedItem(long itemId, long productId, String name, String slug, String imageUrl, int quantity, DesignConfig configuration, BigDecimal unitPrice,
                             BigDecimal lineTotal, List<PriceLine> lines, ItemDetails details, boolean available, List<String> problems) {}
    public record CartView(List<PricedItem> items, int garmentCount, BigDecimal subtotal, boolean checkoutReady) {}
    public record AddRequest(@NotNull Long productId, @Min(1) @Max(10) int quantity, @NotNull DesignConfig configuration) {}
    public record UpdateRequest(@Min(1) @Max(10) Integer quantity, DesignConfig configuration) {}

    private static final int MAX_LINES = 20;
    private final JdbcTemplate jdbc;
    private final PublicCatalogService catalog;
    private final DesignService designs;
    private final ObjectMapper mapper;
    public CartService(JdbcTemplate jdbc, PublicCatalogService catalog, DesignService designs, ObjectMapper mapper) { this.jdbc = jdbc; this.catalog = catalog; this.designs = designs; this.mapper = mapper; }

    public CartView view(long userId) {
        List<PricedItem> items = priceItems(userId);
        BigDecimal subtotal = items.stream().filter(PricedItem::available).filter(i -> i.problems().isEmpty()).map(PricedItem::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        int garments = items.stream().mapToInt(PricedItem::quantity).sum();
        boolean ready = !items.isEmpty() && items.stream().allMatch(i -> i.available() && i.problems().isEmpty());
        return new CartView(items, garments, subtotal, ready);
    }

    /** Prices every cart line from the live catalog. A line that is no longer valid is flagged, never silently repriced from old data. */
    public List<PricedItem> priceItems(long userId) {
        record Row(long id, long productId, int qty, String cfg) {}
        List<Row> rows = jdbc.query("SELECT ci.id, ci.product_id, ci.quantity, ci.configuration::text FROM cart_items ci JOIN carts c ON c.id = ci.cart_id WHERE c.user_id = ? ORDER BY ci.id",
                (rs, i) -> new Row(rs.getLong(1), rs.getLong(2), rs.getInt(3), rs.getString(4)), userId);
        List<PricedItem> out = new ArrayList<>();
        for (Row r : rows) {
            DesignConfig cfg = parse(r.cfg());
            DesignConfig noTailor = new DesignConfig(cfg.sizeCode(), cfg.measurementProfileId(), cfg.fabricId(), cfg.colorId(), cfg.options() == null ? Map.of() : cfg.options(), null);
            ProductPublic p;
            try { p = catalog.byId(r.productId()); }
            catch (BusinessException e) {
                String name = jdbc.queryForList("SELECT name FROM products WHERE id = ?", String.class, r.productId()).stream().findFirst().orElse("Unavailable product");
                out.add(new PricedItem(r.id(), r.productId(), name, "", null, r.qty(), cfg, BigDecimal.ZERO, BigDecimal.ZERO, List.of(), new ItemDetails(null, null, List.of()), false, List.of("This product is no longer available. Remove it to continue.")));
                continue;
            }
            List<String> problems = new ArrayList<>();
            if (noTailor.measurementProfileId() != null && !ownsProfile(userId, noTailor.measurementProfileId())) problems.add("The measurement profile for this item no longer exists. Edit the item to choose another.");
            Quote q = designs.quote(p, noTailor, true);
            problems.addAll(q.errors());
            BigDecimal unit = q.total();
            out.add(new PricedItem(r.id(), p.id(), p.name(), p.slug(), p.images().isEmpty() ? null : p.images().get(0).url(), r.qty(), noTailor, unit, unit.multiply(BigDecimal.valueOf(r.qty())),
                    q.lines(), details(p, noTailor), true, problems));
        }
        return out;
    }

    private ItemDetails details(ProductPublic p, DesignConfig c) {
        String fabric = p.fabrics().stream().filter(f -> c.fabricId() != null && f.id() == c.fabricId()).findFirst().map(FabricPublic::name).orElse(null);
        String color = p.fabrics().stream().filter(f -> c.fabricId() != null && f.id() == c.fabricId()).flatMap(f -> f.colors().stream())
                .filter(x -> c.colorId() != null && x.id() == c.colorId()).findFirst().map(ColorPublic::name).orElse(null);
        List<Selection> sel = new ArrayList<>();
        for (GroupPublic g : p.groups()) {
            Long oid = c.options() == null ? null : c.options().get(g.id());
            if (oid != null) g.options().stream().filter(o -> o.id() == oid).findFirst().ifPresent(o -> sel.add(new Selection(g.name(), o.name())));
        }
        return new ItemDetails(fabric, color, sel);
    }

    private boolean ownsProfile(long userId, long id) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM measurement_profiles WHERE id = ? AND user_id = ? AND deleted_at IS NULL", Integer.class, id, userId);
        return n != null && n > 0;
    }

    @Transactional
    public long add(long userId, AddRequest r) {
        DesignConfig clean = validate(userId, r.productId(), r.configuration());
        long cartId = cartId(userId);
        Integer lines = jdbc.queryForObject("SELECT count(*) FROM cart_items WHERE cart_id = ?", Integer.class, cartId);
        if (lines != null && lines >= MAX_LINES) throw bad("Your cart is full (" + MAX_LINES + " items). Remove something first.");
        touch(cartId);
        return jdbc.queryForObject("INSERT INTO cart_items (cart_id, product_id, quantity, configuration) VALUES (?,?,?,?::jsonb) RETURNING id", Long.class, cartId, r.productId(), r.quantity(), json(clean));
    }

    @Transactional
    public void update(long userId, long itemId, UpdateRequest r) {
        Long productId = jdbc.query("SELECT ci.product_id FROM cart_items ci JOIN carts c ON c.id = ci.cart_id WHERE ci.id = ? AND c.user_id = ?", rs -> rs.next() ? rs.getLong(1) : null, itemId, userId);
        if (productId == null) throw notFound("Cart item");
        if (r.configuration() != null) {
            DesignConfig clean = validate(userId, productId, r.configuration());
            jdbc.update("UPDATE cart_items SET configuration = ?::jsonb, updated_at = now() WHERE id = ?", json(clean), itemId);
        }
        if (r.quantity() != null) jdbc.update("UPDATE cart_items SET quantity = ?, updated_at = now() WHERE id = ?", r.quantity(), itemId);
        jdbc.update("UPDATE carts SET updated_at = now() WHERE user_id = ?", userId);
    }

    @Transactional
    public void remove(long userId, long itemId) {
        if (jdbc.update("DELETE FROM cart_items WHERE id = ? AND cart_id IN (SELECT id FROM carts WHERE user_id = ?)", itemId, userId) == 0) throw notFound("Cart item");
    }

    @Transactional
    public void clear(long userId) { jdbc.update("DELETE FROM cart_items WHERE cart_id IN (SELECT id FROM carts WHERE user_id = ?)", userId); }

    public int garmentCount(long userId) {
        Integer n = jdbc.queryForObject("SELECT COALESCE(SUM(ci.quantity), 0) FROM cart_items ci JOIN carts c ON c.id = ci.cart_id WHERE c.user_id = ?", Integer.class, userId);
        return n == null ? 0 : n;
    }

    /** Adding or editing requires a purchasable product and a complete, valid configuration (including fit). The tailor category is chosen at checkout. */
    private DesignConfig validate(long userId, long productId, DesignConfig c) {
        ProductPublic p = catalog.byId(productId);
        DesignConfig clean = new DesignConfig(c.sizeCode(), c.measurementProfileId(), c.fabricId(), c.colorId(), c.options() == null ? Map.of() : c.options(), null);
        if (clean.measurementProfileId() != null && !ownsProfile(userId, clean.measurementProfileId())) throw bad("That measurement profile does not exist.");
        Quote q = designs.quote(p, clean, true);
        if (!q.valid()) throw bad(q.errors().get(0));
        return clean;
    }

    private long cartId(long userId) {
        jdbc.update("INSERT INTO carts (user_id) VALUES (?) ON CONFLICT (user_id) DO NOTHING", userId);
        return jdbc.queryForObject("SELECT id FROM carts WHERE user_id = ?", Long.class, userId);
    }
    private void touch(long cartId) { jdbc.update("UPDATE carts SET updated_at = now() WHERE id = ?", cartId); }
    private String json(Object o) { try { return mapper.writeValueAsString(o); } catch (JsonProcessingException e) { throw bad("Invalid item."); } }
    private DesignConfig parse(String s) {
        try { return mapper.readValue(s, DesignConfig.class); } catch (JsonProcessingException e) { return new DesignConfig(null, null, null, null, Map.of(), null); }
    }
}
