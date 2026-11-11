package com.clothingplatform.shop;

import static com.clothingplatform.catalog.CatalogSupport.*;

import com.clothingplatform.audit.AuditService;
import com.clothingplatform.catalog.CatalogSupport;
import com.clothingplatform.common.Slugs;
import com.clothingplatform.common.Urls;
import com.clothingplatform.security.CurrentUser;
import com.clothingplatform.shop.ShopDtos.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Trend collections are curated by admins. The schema carries source/external_ref so a real external fashion-trend
 * provider can be plugged in later. Nothing here claims live global trend data.
 */
@Service
public class TrendService {
    private static final Set<String> KINDS = Set.of("SEASONAL", "CASUAL", "FORMAL", "TRADITIONAL", "WEDDING", "FESTIVAL", "MEN", "WOMEN", "KIDS", "OTHER");

    public record CollectionView(long id, String name, String slug, String kind, String description, String imageUrl, int sortOrder, boolean active, String source, List<Long> productIds) {}
    public record CollectionRequest(@NotBlank @Size(max = 120) String name, @NotBlank String kind, @Size(max = 1000) String description, String imageUrl, Integer sortOrder, Boolean active) {}

    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final CatalogSupport support;
    private final PublicCatalogService catalog;
    public TrendService(JdbcTemplate jdbc, AuditService audit, CatalogSupport support, PublicCatalogService catalog) { this.jdbc = jdbc; this.audit = audit; this.support = support; this.catalog = catalog; }

    private long actor() { return CurrentUser.require().getId(); }

    // ---- public ----
    public List<CollectionPublic> publicCollections(int productsEach) {
        Map<Long, List<Long>> ids = new HashMap<>();
        jdbc.query("SELECT collection_id, product_id FROM trend_collection_products ORDER BY sort_order, product_id", rs -> { ids.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>()).add(rs.getLong(2)); });
        record Row(long id, String name, String slug, String kind, String description, String image) {}
        List<Row> rows = jdbc.query("SELECT id, name, slug, kind, description, image_url FROM trend_collections WHERE deleted_at IS NULL AND is_active ORDER BY sort_order, name",
                (rs, i) -> new Row(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6)));
        List<CollectionPublic> out = new ArrayList<>();
        for (Row r : rows) {
            List<Long> pids = ids.getOrDefault(r.id(), List.of());
            List<ProductCard> cards = catalog.cardsFor(pids.subList(0, Math.min(pids.size(), Math.max(1, productsEach))));
            if (!cards.isEmpty()) out.add(new CollectionPublic(r.id(), r.name(), r.slug(), r.kind(), r.description(), r.image(), cards));   // empty collections stay hidden
        }
        return out;
    }

    public TrendingData trending() {
        return new TrendingData(publicCollections(24), catalog.search(null, null, null, null, null, null, null, null, true, "newest", 0, 24).items());
    }

    // ---- admin ----
    public List<CollectionView> list() {
        Map<Long, List<Long>> ids = new HashMap<>();
        jdbc.query("SELECT collection_id, product_id FROM trend_collection_products ORDER BY sort_order, product_id", rs -> { ids.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>()).add(rs.getLong(2)); });
        return jdbc.query("SELECT id, name, slug, kind, description, image_url, sort_order, is_active, source FROM trend_collections WHERE deleted_at IS NULL ORDER BY sort_order, name",
                (rs, i) -> new CollectionView(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getInt(7), rs.getBoolean(8), rs.getString(9), ids.getOrDefault(rs.getLong(1), List.of())));
    }

    @Transactional
    public long create(CollectionRequest r) {
        requireKind(r.kind());
        long id = jdbc.queryForObject("INSERT INTO trend_collections (name, slug, kind, description, image_url, sort_order, is_active) VALUES (?,?,?,?,?,?,?) RETURNING id", Long.class,
                r.name().trim(), Slugs.unique(jdbc, "trend_collections", r.name(), null), r.kind(), r.description(), Urls.clean(r.imageUrl()), r.sortOrder() == null ? 0 : r.sortOrder(), r.active() == null || r.active());
        audit.audit(actor(), "COLLECTION_CREATED", "COLLECTION", id, null, r, null);
        return id;
    }

    @Transactional
    public void update(long id, CollectionRequest r) {
        requireKind(r.kind());
        if (jdbc.update("UPDATE trend_collections SET name=?, slug=?, kind=?, description=?, image_url=?, sort_order=?, is_active=?, updated_at=now() WHERE id=? AND deleted_at IS NULL",
                r.name().trim(), Slugs.unique(jdbc, "trend_collections", r.name(), id), r.kind(), r.description(), Urls.clean(r.imageUrl()), r.sortOrder() == null ? 0 : r.sortOrder(), r.active() == null || r.active(), id) == 0) throw notFound("Collection");
        audit.audit(actor(), "COLLECTION_UPDATED", "COLLECTION", id, null, r, null);
    }

    @Transactional
    public void delete(long id) {
        if (jdbc.update("UPDATE trend_collections SET deleted_at = now(), updated_at = now() WHERE id = ? AND deleted_at IS NULL", id) == 0) throw notFound("Collection");
        audit.audit(actor(), "COLLECTION_DELETED", "COLLECTION", id, null, null, null);
    }

    @Transactional
    public void setProducts(long id, List<Long> productIds) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM trend_collections WHERE id = ? AND deleted_at IS NULL", Integer.class, id);
        if (n == null || n == 0) throw notFound("Collection");
        List<Long> distinct = new ArrayList<>(new LinkedHashSet<>(productIds));
        if (distinct.size() > 200) throw bad("A collection can hold up to 200 products.");
        support.requireIds("products", distinct, "products");
        jdbc.update("DELETE FROM trend_collection_products WHERE collection_id = ?", id);
        int i = 0;
        for (long pid : distinct) jdbc.update("INSERT INTO trend_collection_products (collection_id, product_id, sort_order) VALUES (?,?,?)", id, pid, i++);
        audit.audit(actor(), "COLLECTION_PRODUCTS_SET", "COLLECTION", id, null, distinct, null);
    }

    private void requireKind(String k) { if (!KINDS.contains(k)) throw bad("Unknown collection type."); }
}
