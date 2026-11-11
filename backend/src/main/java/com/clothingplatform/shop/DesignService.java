package com.clothingplatform.shop;

import static com.clothingplatform.catalog.CatalogSupport.*;

import com.clothingplatform.common.Sql;
import com.clothingplatform.shop.ShopDtos.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The server-side authority on what a customization is worth and whether it is allowed. The same rules will
 * price cart items and orders (Phase 8), so the browser never decides a price.
 */
@Service
public class DesignService {
    public record DesignRequest(@NotNull Long productId, @NotBlank @Size(max = 100) String name, @NotNull DesignConfig configuration) {}
    public record DesignView(long id, long productId, String productName, String productSlug, String imageUrl, boolean available, String name, JsonNode configuration, Instant updatedAt) {}
    public record DesignDetail(DesignView design, Quote quote, ProductPublic product) {}

    private static final int MAX_DESIGNS = 50;

    private final JdbcTemplate jdbc;
    private final PublicCatalogService catalog;
    private final ObjectMapper mapper;
    public DesignService(JdbcTemplate jdbc, PublicCatalogService catalog, ObjectMapper mapper) { this.jdbc = jdbc; this.catalog = catalog; this.mapper = mapper; }

    /** Looks up the tailor category chosen in the config (active only), so the price comes from the admin-set value. */
    public Quote quoteResolved(ProductPublic p, DesignConfig c, boolean requireFit) {
        return quoteWith(p, c, requireFit, resolveTailor(c.tailorCategoryId()));
    }

    private TailorCategoryPublic resolveTailor(Long id) {
        if (id == null) return null;
        List<TailorCategoryPublic> l = jdbc.query("SELECT id, name, description, level, stitching_price FROM tailor_categories WHERE id = ? AND deleted_at IS NULL AND is_active",
                (rs, i) -> new TailorCategoryPublic(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getInt(4), rs.getBigDecimal(5)), id);
        return l.isEmpty() ? null : l.get(0);
    }

    public Quote quote(ProductPublic p, DesignConfig c, boolean requireFit) { return quoteWith(p, c, requireFit, null); }

    /** @param requireFit true when saving or ordering: a size or a measurement profile must be chosen. @param tailor the resolved category for c.tailorCategoryId(), or null */
    public Quote quoteWith(ProductPublic p, DesignConfig c, boolean requireFit, TailorCategoryPublic tailor) {
        List<String> errors = new ArrayList<>();
        List<PriceLine> lines = new ArrayList<>();
        BigDecimal total = p.basePrice();

        if (requireFit) {
            if (c.sizeCode() == null && c.measurementProfileId() == null) errors.add("Choose a size or one of your measurement profiles.");
            if (c.sizeCode() != null && c.measurementProfileId() != null) errors.add("Choose either a standard size or custom measurements, not both.");
        }
        if (c.sizeCode() != null && p.sizes().stream().noneMatch((s) -> s.code().equals(c.sizeCode()))) errors.add("That size is not available for this product.");

        // fabric and colour
        if (p.fabrics().isEmpty()) {
            if (c.fabricId() != null) errors.add("This product has no fabric choice.");
        } else if (c.fabricId() == null) {
            errors.add("Choose a fabric.");
        } else {
            Optional<FabricPublic> fabric = p.fabrics().stream().filter(f -> f.id() == c.fabricId()).findFirst();
            if (fabric.isEmpty()) errors.add("That fabric is not available for this product.");
            else {
                FabricPublic f = fabric.get();
                if (f.priceDelta().signum() > 0) { lines.add(new PriceLine("Fabric: " + f.name(), f.priceDelta())); total = total.add(f.priceDelta()); }
                if (f.colors().isEmpty()) { if (c.colorId() != null) errors.add("This fabric has no colour choice."); }
                else if (c.colorId() == null) errors.add("Choose a colour.");
                else if (f.colors().stream().noneMatch(col -> col.id() == c.colorId())) errors.add("That colour is not available in the chosen fabric.");
            }
        }

        // customization options: one per group, required groups must be answered, nothing unknown
        Map<Long, Long> chosen = c.options() == null ? Map.of() : c.options();
        Set<Long> knownGroups = new HashSet<>();
        for (GroupPublic g : p.groups()) {
            knownGroups.add(g.id());
            Long optionId = chosen.get(g.id());
            if (optionId == null) { if (g.required()) errors.add("Choose an option for “" + g.name() + "”."); continue; }
            Optional<OptionPublic> o = g.options().stream().filter(x -> x.id() == optionId).findFirst();
            if (o.isEmpty()) { errors.add("That choice is not available for “" + g.name() + "”."); continue; }
            if (o.get().priceDelta().signum() > 0) { lines.add(new PriceLine(g.name() + ": " + o.get().name(), o.get().priceDelta())); total = total.add(o.get().priceDelta()); }
        }
        for (Long gid : chosen.keySet()) if (!knownGroups.contains(gid)) errors.add("This product does not offer that customization.");

        if (c.tailorCategoryId() != null) {
            if (tailor == null) errors.add("That tailor category is not available.");
            else { lines.add(new PriceLine("Stitching: " + tailor.name() + " tailor", tailor.stitchingPrice())); total = total.add(tailor.stitchingPrice()); }
        }
        return new Quote(errors.isEmpty(), errors, p.basePrice(), lines, total);
    }

    // ---------- saved designs ----------

    public List<DesignView> list(long userId) {
        return jdbc.query("""
            SELECT d.id, d.product_id, d.name, d.configuration::text AS cfg, d.updated_at, p.name AS pname, p.slug,
                   (p.deleted_at IS NULL AND p.status = 'ACTIVE') AS available,
                   (SELECT url FROM product_images i WHERE i.product_id = p.id ORDER BY i.is_primary DESC, i.sort_order, i.id LIMIT 1) AS img
            FROM saved_designs d JOIN products p ON p.id = d.product_id
            WHERE d.user_id = ? AND d.deleted_at IS NULL ORDER BY d.updated_at DESC""",
            (rs, i) -> new DesignView(rs.getLong("id"), rs.getLong("product_id"), rs.getString("pname"), rs.getString("slug"), rs.getString("img"),
                    rs.getBoolean("available"), rs.getString("name"), parse(rs.getString("cfg")), Sql.ts(rs, "updated_at")), userId);
    }

    public DesignDetail get(long userId, long id) {
        DesignView d = list(userId).stream().filter(x -> x.id() == id).findFirst().orElseThrow(() -> notFound("Design"));
        if (!d.available()) throw new com.clothingplatform.exception.BusinessException("PRODUCT_UNAVAILABLE", "This product is no longer available.", org.springframework.http.HttpStatus.GONE);
        ProductPublic p = catalog.byId(d.productId());
        return new DesignDetail(d, quoteResolved(p, toConfig(d.configuration()), false), p);
    }

    @Transactional
    public long create(long userId, DesignRequest r) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM saved_designs WHERE user_id = ? AND deleted_at IS NULL", Integer.class, userId);
        if (count != null && count >= MAX_DESIGNS) throw bad("You can keep up to " + MAX_DESIGNS + " saved designs. Delete one first.");
        String cfg = validated(userId, r);
        return jdbc.queryForObject("INSERT INTO saved_designs (user_id, product_id, name, configuration) VALUES (?,?,?,?::jsonb) RETURNING id", Long.class, userId, r.productId(), r.name().trim(), cfg);
    }

    @Transactional
    public void update(long userId, long id, DesignRequest r) {
        String cfg = validated(userId, r);
        if (jdbc.update("UPDATE saved_designs SET product_id = ?, name = ?, configuration = ?::jsonb, updated_at = now() WHERE id = ? AND user_id = ? AND deleted_at IS NULL",
                r.productId(), r.name().trim(), cfg, id, userId) == 0) throw notFound("Design");
    }

    @Transactional
    public void delete(long userId, long id) {
        if (jdbc.update("UPDATE saved_designs SET deleted_at = now(), updated_at = now() WHERE id = ? AND user_id = ? AND deleted_at IS NULL", id, userId) == 0) throw notFound("Design");
    }

    /** Validates against the live catalog and the customer's own measurement profiles; returns the normalised JSON. */
    private String validated(long userId, DesignRequest r) {
        ProductPublic p = catalog.byId(r.productId());   // 404 if not purchasable
        DesignConfig c = r.configuration();
        if (c.measurementProfileId() != null) {
            Integer own = jdbc.queryForObject("SELECT count(*) FROM measurement_profiles WHERE id = ? AND user_id = ? AND deleted_at IS NULL", Integer.class, c.measurementProfileId(), userId);
            if (own == null || own == 0) throw bad("That measurement profile does not exist.");
        }
        Quote q = quoteResolved(p, c, true);
        if (!q.valid()) throw bad(q.errors().get(0));
        try { return mapper.writeValueAsString(c); } catch (JsonProcessingException e) { throw bad("Invalid design."); }
    }

    private DesignConfig toConfig(JsonNode n) {
        try { return mapper.treeToValue(n, DesignConfig.class); } catch (JsonProcessingException e) { return new DesignConfig(null, null, null, null, Map.of(), null); }
    }
    private JsonNode parse(String s) {
        try { return mapper.readTree(s); } catch (JsonProcessingException e) { return null; }
    }
}
