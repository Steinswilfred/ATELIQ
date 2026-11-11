package com.clothingplatform.catalog;

import static com.clothingplatform.catalog.CatalogSupport.*;

import com.clothingplatform.audit.AuditService;
import com.clothingplatform.common.PageResult;
import com.clothingplatform.common.Slugs;
import com.clothingplatform.common.Sql;
import com.clothingplatform.common.Urls;
import com.clothingplatform.security.CurrentUser;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.validation.Valid;

@Service
public class ProductService {
    public record ProductRow(long id, String name, String slug, long categoryId, String categoryName, BigDecimal basePrice, String status,
                             boolean featured, boolean trending, String primaryImage) {}
    public record ImageView(long id, String url, String alt, boolean primary, int sortOrder) {}
    public record GroupLink(@NotNull Long groupId, boolean required) {}
    public record OptionLink(@NotNull Long optionId, boolean isDefault) {}
    public record ProductDetail(ProductRow summary, String description, String modelUrl, List<ImageView> images, List<Long> sizeIds,
                                List<Long> fabricIds, List<GroupLink> groups, List<OptionLink> options) {}
    public record ProductRequest(@NotNull Long categoryId, @NotBlank @Size(max = 160) String name, @Size(max = 20000) String description,
                                 @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal basePrice, Boolean featured, Boolean trending, String modelUrl) {}
    public record IdsRequest(@NotNull List<Long> ids) {}
    public record CustomizationRequest(@NotNull List<@Valid GroupLink> groups, @NotNull List<@Valid OptionLink> options) {}
    public record ImageRequest(@NotBlank String url, @Size(max = 200) String alt) {}
    public record StatusRequest(@NotBlank String status) {}

    private static final Set<String> STATUSES = Set.of("DRAFT", "ACTIVE", "ARCHIVED");
    private static final String SELECT = """
        SELECT p.id, p.name, p.slug, p.category_id, c.name AS cat, p.base_price, p.status, p.is_featured, p.is_trending,
               (SELECT url FROM product_images i WHERE i.product_id = p.id ORDER BY i.is_primary DESC, i.sort_order, i.id LIMIT 1) AS img
        FROM products p JOIN categories c ON c.id = p.category_id WHERE p.deleted_at IS NULL""";

    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final CatalogSupport support;
    public ProductService(JdbcTemplate jdbc, AuditService audit, CatalogSupport support) { this.jdbc = jdbc; this.audit = audit; this.support = support; }

    private long actor() { return CurrentUser.require().getId(); }

    private static ProductRow row(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ProductRow(rs.getLong("id"), rs.getString("name"), rs.getString("slug"), rs.getLong("category_id"), rs.getString("cat"),
                rs.getBigDecimal("base_price"), rs.getString("status"), rs.getBoolean("is_featured"), rs.getBoolean("is_trending"), rs.getString("img"));
    }

    public PageResult<ProductRow> list(Long categoryId, String status, String q, int page, int size) {
        StringBuilder where = new StringBuilder();
        List<Object> args = new ArrayList<>();
        if (categoryId != null) { where.append(" AND (p.category_id = ? OR p.category_id IN (SELECT id FROM categories WHERE parent_id = ?))"); args.add(categoryId); args.add(categoryId); }
        if (status != null && !status.isBlank()) { if (!STATUSES.contains(status)) throw bad("Unknown status."); where.append(" AND p.status = ?"); args.add(status); }
        if (q != null && !q.isBlank()) { where.append(" AND p.name ILIKE ?"); args.add("%" + q.trim() + "%"); }
        int p = Sql.clampPage(page), s = Sql.clampSize(size);
        Long total = jdbc.queryForObject("SELECT count(*) FROM products p WHERE p.deleted_at IS NULL" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args); pageArgs.add(s); pageArgs.add(p * s);
        List<ProductRow> items = jdbc.query(SELECT + where + " ORDER BY p.created_at DESC LIMIT ? OFFSET ?", (rs, i) -> row(rs), pageArgs.toArray());
        return new PageResult<>(items, total == null ? 0 : total, p, s);
    }

    public ProductDetail detail(long id) {
        List<ProductRow> rows = jdbc.query(SELECT + " AND p.id = ?", (rs, i) -> row(rs), id);
        if (rows.isEmpty()) throw notFound("Product");
        Map<String, Object> extra = jdbc.queryForMap("SELECT description, model_url FROM products WHERE id = ?", id);
        List<ImageView> images = jdbc.query("SELECT id, url, alt, is_primary, sort_order FROM product_images WHERE product_id = ? ORDER BY is_primary DESC, sort_order, id",
                (rs, i) -> new ImageView(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getBoolean(4), rs.getInt(5)), id);
        List<Long> sizeIds = jdbc.queryForList("SELECT size_id FROM product_sizes WHERE product_id = ?", Long.class, id);
        List<Long> fabricIds = jdbc.queryForList("SELECT pf.fabric_id FROM product_fabrics pf JOIN fabrics f ON f.id = pf.fabric_id WHERE pf.product_id = ? AND f.deleted_at IS NULL", Long.class, id);
        List<GroupLink> groups = jdbc.query("""
            SELECT pg.group_id, pg.is_required FROM product_customization_groups pg JOIN customization_groups g ON g.id = pg.group_id
            WHERE pg.product_id = ? AND g.deleted_at IS NULL ORDER BY pg.sort_order""", (rs, i) -> new GroupLink(rs.getLong(1), rs.getBoolean(2)), id);
        List<OptionLink> options = jdbc.query("""
            SELECT po.option_id, po.is_default FROM product_customization_options po JOIN customization_options o ON o.id = po.option_id
            WHERE po.product_id = ? AND o.deleted_at IS NULL ORDER BY po.sort_order""", (rs, i) -> new OptionLink(rs.getLong(1), rs.getBoolean(2)), id);
        return new ProductDetail(rows.get(0), (String) extra.get("description"), (String) extra.get("model_url"), images, sizeIds, fabricIds, groups, options);
    }

    @Transactional
    public long create(ProductRequest r) {
        requireCategory(r.categoryId());
        String slug = Slugs.unique(jdbc, "products", r.name(), null);
        long id = jdbc.queryForObject("""
            INSERT INTO products (category_id, name, slug, description, base_price, is_featured, is_trending, model_url) VALUES (?,?,?,?,?,?,?,?) RETURNING id""", Long.class,
            r.categoryId(), r.name().trim(), slug, r.description(), r.basePrice(), Boolean.TRUE.equals(r.featured()), Boolean.TRUE.equals(r.trending()), Urls.cleanModel(r.modelUrl()));
        audit.audit(actor(), "PRODUCT_CREATED", "PRODUCT", id, null, r, null);
        return id;
    }

    @Transactional
    public void update(long id, ProductRequest r) {
        requireProduct(id);
        requireCategory(r.categoryId());
        String slug = Slugs.unique(jdbc, "products", r.name(), id);
        jdbc.update("""
            UPDATE products SET category_id=?, name=?, slug=?, description=?, base_price=?, is_featured=?, is_trending=?, model_url=?, updated_at=now() WHERE id=?""",
            r.categoryId(), r.name().trim(), slug, r.description(), r.basePrice(), Boolean.TRUE.equals(r.featured()), Boolean.TRUE.equals(r.trending()), Urls.cleanModel(r.modelUrl()), id);
        audit.audit(actor(), "PRODUCT_UPDATED", "PRODUCT", id, null, r, null);
    }

    @Transactional
    public void setStatus(long id, String status) {
        if (!STATUSES.contains(status)) throw bad("Status must be DRAFT, ACTIVE or ARCHIVED.");
        requireProduct(id);
        jdbc.update("UPDATE products SET status = ?, updated_at = now() WHERE id = ?", status, id);
        audit.audit(actor(), "PRODUCT_STATUS_" + status, "PRODUCT", id, null, status, null);
    }

    @Transactional
    public void delete(long id) {
        requireProduct(id);
        jdbc.update("UPDATE products SET deleted_at = now(), updated_at = now() WHERE id = ?", id);
        audit.audit(actor(), "PRODUCT_DELETED", "PRODUCT", id, null, null, null);
    }

    @Transactional
    public void setSizes(long id, List<Long> ids) {
        requireProduct(id);
        List<Long> clean = support.requireIds("sizes", ids, "sizes");
        jdbc.update("DELETE FROM product_sizes WHERE product_id = ?", id);
        for (long s : clean) jdbc.update("INSERT INTO product_sizes (product_id, size_id) VALUES (?,?)", id, s);
        audit.audit(actor(), "PRODUCT_SIZES_SET", "PRODUCT", id, null, clean, null);
    }

    @Transactional
    public void setFabrics(long id, List<Long> ids) {
        requireProduct(id);
        List<Long> clean = support.requireIds("fabrics", ids, "fabrics");
        jdbc.update("DELETE FROM product_fabrics WHERE product_id = ?", id);
        for (long f : clean) jdbc.update("INSERT INTO product_fabrics (product_id, fabric_id) VALUES (?,?)", id, f);
        audit.audit(actor(), "PRODUCT_FABRICS_SET", "PRODUCT", id, null, clean, null);
    }

    /** Which groups a product offers, and which options of each group; at most one default option per group. */
    @Transactional
    public void setCustomization(long id, CustomizationRequest r) {
        requireProduct(id);
        List<Long> groupIds = support.requireIds("customization_groups", r.groups().stream().map(GroupLink::groupId).toList(), "groups");
        if (groupIds.size() != r.groups().size()) throw bad("A group is listed twice.");
        List<Long> optionIds = support.requireIds("customization_options", r.options().stream().map(OptionLink::optionId).toList(), "options");
        if (optionIds.size() != r.options().size()) throw bad("An option is listed twice.");
        Map<Long, Long> groupOf = new HashMap<>();
        jdbc.query("SELECT id, group_id FROM customization_options WHERE id IN (" + String.join(",", Collections.nCopies(Math.max(1, optionIds.size()), "?")) + ")",
                rs -> { groupOf.put(rs.getLong(1), rs.getLong(2)); }, optionIds.isEmpty() ? new Object[]{-1L} : optionIds.toArray());
        Map<Long, Integer> defaults = new HashMap<>();
        for (OptionLink o : r.options()) {
            Long g = groupOf.get(o.optionId());
            if (g == null || !groupIds.contains(g)) throw bad("Every option must belong to a group that is added to this product.");
            if (o.isDefault() && defaults.merge(g, 1, Integer::sum) > 1) throw bad("Only one default option is allowed per group.");
        }
        jdbc.update("DELETE FROM product_customization_options WHERE product_id = ?", id);
        jdbc.update("DELETE FROM product_customization_groups WHERE product_id = ?", id);
        int n = 0;
        for (GroupLink g : r.groups()) jdbc.update("INSERT INTO product_customization_groups (product_id, group_id, is_required, sort_order) VALUES (?,?,?,?)", id, g.groupId(), g.required(), n++);
        n = 0;
        for (OptionLink o : r.options()) jdbc.update("INSERT INTO product_customization_options (product_id, option_id, is_default, sort_order) VALUES (?,?,?,?)", id, o.optionId(), o.isDefault(), n++);
        audit.audit(actor(), "PRODUCT_CUSTOMIZATION_SET", "PRODUCT", id, null, r, null);
    }

    @Transactional
    public long addImage(long id, ImageRequest r) {
        requireProduct(id);
        String url = Urls.clean(r.url());
        Integer count = jdbc.queryForObject("SELECT count(*) FROM product_images WHERE product_id = ?", Integer.class, id);
        Integer max = jdbc.queryForObject("SELECT COALESCE(MAX(sort_order), 0) FROM product_images WHERE product_id = ?", Integer.class, id);
        long imageId = jdbc.queryForObject("INSERT INTO product_images (product_id, url, alt, sort_order, is_primary) VALUES (?,?,?,?,?) RETURNING id", Long.class,
                id, url, r.alt(), (max == null ? 0 : max) + 1, count == null || count == 0);
        audit.audit(actor(), "PRODUCT_IMAGE_ADDED", "PRODUCT", id, null, url, null);
        return imageId;
    }

    @Transactional
    public void setPrimaryImage(long id, long imageId) {
        requireProduct(id);
        if (jdbc.queryForObject("SELECT count(*) FROM product_images WHERE id = ? AND product_id = ?", Integer.class, imageId, id) == 0) throw notFound("Image");
        jdbc.update("UPDATE product_images SET is_primary = FALSE WHERE product_id = ?", id);
        jdbc.update("UPDATE product_images SET is_primary = TRUE WHERE id = ?", imageId);
    }

    @Transactional
    public void deleteImage(long id, long imageId) {
        requireProduct(id);
        Boolean wasPrimary = jdbc.query("SELECT is_primary FROM product_images WHERE id = ? AND product_id = ?", rs -> rs.next() ? rs.getBoolean(1) : null, imageId, id);
        if (wasPrimary == null) throw notFound("Image");
        jdbc.update("DELETE FROM product_images WHERE id = ?", imageId);
        if (wasPrimary) jdbc.update("UPDATE product_images SET is_primary = TRUE WHERE id = (SELECT id FROM product_images WHERE product_id = ? ORDER BY sort_order, id LIMIT 1)", id);
        audit.audit(actor(), "PRODUCT_IMAGE_REMOVED", "PRODUCT", id, null, imageId, null);
    }

    private void requireProduct(long id) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM products WHERE id = ? AND deleted_at IS NULL", Integer.class, id);
        if (n == null || n == 0) throw notFound("Product");
    }
    private void requireCategory(Long id) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM categories WHERE id = ? AND deleted_at IS NULL", Integer.class, id);
        if (n == null || n == 0) throw bad("Choose an existing category.");
    }
}
