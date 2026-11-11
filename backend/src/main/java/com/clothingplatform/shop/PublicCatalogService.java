package com.clothingplatform.shop;

import static com.clothingplatform.catalog.CatalogSupport.*;

import com.clothingplatform.common.PageResult;
import com.clothingplatform.common.Sql;
import com.clothingplatform.shop.ShopDtos.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Read-only customer-facing catalog. Only ACTIVE products in active categories, with active fabrics, colours and options. */
@Service
public class PublicCatalogService {
    private static final Map<String, String> SORTS = Map.of("newest", "p.created_at DESC", "price_asc", "p.base_price ASC, p.id", "price_desc", "p.base_price DESC, p.id", "name", "p.name ASC");
    private static final String CARD_FROM = """
        FROM products p JOIN categories c ON c.id = p.category_id LEFT JOIN categories parent ON parent.id = c.parent_id
        WHERE p.deleted_at IS NULL AND p.status = 'ACTIVE' AND c.deleted_at IS NULL AND c.is_active
          AND (parent.id IS NULL OR (parent.deleted_at IS NULL AND parent.is_active))""";
    private static final String CARD_COLS = """
        SELECT p.id, p.name, p.slug, p.base_price, c.name AS cat, p.is_featured, p.is_trending,
               (SELECT i.url FROM product_images i WHERE i.product_id = p.id ORDER BY i.is_primary DESC, i.sort_order, i.id LIMIT 1) AS img   
                """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    public PublicCatalogService(JdbcTemplate jdbc, ObjectMapper mapper) { this.jdbc = jdbc; this.mapper = mapper; }

    private static ProductCard card(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ProductCard(rs.getLong("id"), rs.getString("name"), rs.getString("slug"), rs.getBigDecimal("base_price"), rs.getString("cat"),
                rs.getString("img"), rs.getBoolean("is_featured"), rs.getBoolean("is_trending"));
    }

    public List<TailorCategoryPublic> tailorCategories() {
        return jdbc.query("SELECT id, name, description, level, stitching_price FROM tailor_categories WHERE deleted_at IS NULL AND is_active ORDER BY level, name",
                (rs, i) -> new TailorCategoryPublic(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getInt(4), rs.getBigDecimal(5)));
    }

    public List<CategoryNode> categoryTree() {
        record Row(long id, Long parentId, String name, String slug, String img, int count) {}
        List<Row> rows = jdbc.query("""
            SELECT c.id, c.parent_id, c.name, c.slug, c.image_url,
                   (SELECT count(*) FROM products p WHERE p.category_id = c.id AND p.deleted_at IS NULL AND p.status = 'ACTIVE') AS pc
            FROM categories c WHERE c.deleted_at IS NULL AND c.is_active ORDER BY c.sort_order, c.name""",
            (rs, i) -> new Row(rs.getLong(1), (Long) rs.getObject(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getInt(6)));
        List<CategoryNode> roots = new ArrayList<>();
        for (Row r : rows) {
            if (r.parentId() != null) continue;
            List<CategoryNode> kids = rows.stream().filter(k -> Objects.equals(k.parentId(), r.id()))
                    .map(k -> new CategoryNode(k.id(), k.name(), k.slug(), k.img(), k.count(), List.of())).toList();
            int total = r.count() + kids.stream().mapToInt(CategoryNode::productCount).sum();
            roots.add(new CategoryNode(r.id(), r.name(), r.slug(), r.img(), total, kids));
        }
        return roots;
    }

    public PageResult<ProductCard> search(String section, String category, String q, BigDecimal minPrice, BigDecimal maxPrice, Long fabricId,
                                          String size, Boolean featured, Boolean trending, String sort, int page, int pageSize) {
        StringBuilder where = new StringBuilder();
        List<Object> args = new ArrayList<>();
        if (notBlank(section)) { where.append(" AND (c.slug = ? OR parent.slug = ?)"); args.add(section); args.add(section); }
        if (notBlank(category)) { where.append(" AND (c.slug = ? OR parent.slug = ?)"); args.add(category); args.add(category); }
        if (notBlank(q)) { where.append(" AND (p.name ILIKE ? OR p.description ILIKE ?)"); String like = "%" + q.trim() + "%"; args.add(like); args.add(like); }
        if (minPrice != null) { where.append(" AND p.base_price >= ?"); args.add(minPrice); }
        if (maxPrice != null) { where.append(" AND p.base_price <= ?"); args.add(maxPrice); }
        if (fabricId != null) { where.append(" AND EXISTS (SELECT 1 FROM product_fabrics pf WHERE pf.product_id = p.id AND pf.fabric_id = ?)"); args.add(fabricId); }
        if (notBlank(size)) { where.append(" AND EXISTS (SELECT 1 FROM product_sizes ps JOIN sizes s ON s.id = ps.size_id WHERE ps.product_id = p.id AND s.code = ? AND s.is_active)"); args.add(size.toUpperCase()); }
        if (Boolean.TRUE.equals(featured)) where.append(" AND p.is_featured");
        if (Boolean.TRUE.equals(trending)) where.append(" AND p.is_trending");
        String order = SORTS.getOrDefault(sort == null ? "newest" : sort, SORTS.get("newest"));
        int pg = Sql.clampPage(page), sz = Math.max(1, Math.min(48, pageSize));
        Long total = jdbc.queryForObject("SELECT count(*) " + CARD_FROM + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args); pageArgs.add(sz); pageArgs.add(pg * sz);
        List<ProductCard> items = jdbc.query(CARD_COLS + CARD_FROM + where + " ORDER BY " + order + " LIMIT ? OFFSET ?", (rs, i) -> card(rs), pageArgs.toArray());
        return new PageResult<>(items, total == null ? 0 : total, pg, sz);
    }

    /** Cards for specific ids, in the given order; inactive or missing products are skipped. */
    public List<ProductCard> cardsFor(List<Long> ids) {
        if (ids.isEmpty()) return List.of();
        String in = String.join(",", Collections.nCopies(ids.size(), "?"));
        Map<Long, ProductCard> byId = new HashMap<>();
        jdbc.query(CARD_COLS + CARD_FROM + " AND p.id IN (" + in + ")", rs -> { ProductCard c = card(rs); byId.put(c.id(), c); }, ids.toArray());
        return ids.stream().map(byId::get).filter(Objects::nonNull).toList();
    }

    public ProductPublic bySlug(String slug) { return load("p.slug = ?", slug); }
    public ProductPublic byId(long id) { return load("p.id = ?", id); }

    private ProductPublic load(String predicate, Object arg) {   // predicate is an internal constant
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT p.id, p.name, p.slug, p.description, p.base_price, p.model_url, c.name AS cat, parent.name AS section "
                + CARD_FROM + " AND " + predicate, arg);
        if (rows.isEmpty()) throw notFound("Product");
        Map<String, Object> r = rows.get(0);
        long id = ((Number) r.get("id")).longValue();
        String section = r.get("section") != null ? (String) r.get("section") : (String) r.get("cat");

        List<ImagePublic> images = jdbc.query("SELECT url, alt FROM product_images WHERE product_id = ? ORDER BY is_primary DESC, sort_order, id", (rs, i) -> new ImagePublic(rs.getString(1), rs.getString(2)), id);
        List<SizePublic> sizes = jdbc.query("SELECT s.code, s.name FROM product_sizes ps JOIN sizes s ON s.id = ps.size_id WHERE ps.product_id = ? AND s.is_active ORDER BY s.sort_order", (rs, i) -> new SizePublic(rs.getString(1), rs.getString(2)), id);

        Map<Long, List<ColorPublic>> colors = new HashMap<>();
        jdbc.query("SELECT fc.fabric_id, c.id, c.name, c.hex_code FROM fabric_colors fc JOIN colors c ON c.id = fc.color_id WHERE c.is_active ORDER BY c.name",
                rs -> { colors.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>()).add(new ColorPublic(rs.getLong(2), rs.getString(3), rs.getString(4))); });
        List<FabricPublic> fabrics = jdbc.query("""
            SELECT f.id, f.name, f.material, f.description, f.price_delta, f.image_url FROM product_fabrics pf JOIN fabrics f ON f.id = pf.fabric_id
            WHERE pf.product_id = ? AND f.is_active AND f.deleted_at IS NULL ORDER BY f.name""",
            (rs, i) -> new FabricPublic(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getBigDecimal(5), rs.getString(6), colors.getOrDefault(rs.getLong(1), List.of())), id);

        Map<Long, List<OptionPublic>> options = new LinkedHashMap<>();
        jdbc.query("""
            SELECT o.group_id, o.id, o.code, o.name, o.description, o.image_url, o.price_delta, po.is_default, o.render_binding::text AS rb
            FROM product_customization_options po JOIN customization_options o ON o.id = po.option_id
            WHERE po.product_id = ? AND o.is_active AND o.deleted_at IS NULL ORDER BY po.sort_order""", rs -> {
            options.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>()).add(new OptionPublic(rs.getLong(2), rs.getString(3), rs.getString(4), rs.getString(5),
                    rs.getString(6), rs.getBigDecimal(7), rs.getBoolean(8), json(rs.getString("rb"))));
        }, id);
        List<GroupPublic> groups = jdbc.query("""
            SELECT g.id, g.code, g.name, g.description, pg.is_required FROM product_customization_groups pg JOIN customization_groups g ON g.id = pg.group_id
            WHERE pg.product_id = ? AND g.is_active AND g.deleted_at IS NULL ORDER BY pg.sort_order""",
            (rs, i) -> new GroupPublic(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getBoolean(5), options.getOrDefault(rs.getLong(1), List.of())), id)
            .stream().filter(g -> !g.options().isEmpty()).toList();

        return new ProductPublic(id, (String) r.get("name"), (String) r.get("slug"), (String) r.get("description"), (BigDecimal) r.get("base_price"),
                (String) r.get("cat"), section, (String) r.get("model_url"), images, sizes, fabrics, groups);
    }

    private JsonNode json(String s) {
        if (s == null) return null;
        try { return mapper.readTree(s); } catch (JsonProcessingException e) { return null; }
    }
    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }
}
