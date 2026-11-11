package com.clothingplatform.catalog;

import static com.clothingplatform.catalog.CatalogSupport.*;

import com.clothingplatform.audit.AuditService;
import com.clothingplatform.common.Slugs;
import com.clothingplatform.common.Urls;
import com.clothingplatform.security.CurrentUser;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {
    public record CategoryView(long id, Long parentId, String name, String slug, String description, String imageUrl, int sortOrder, boolean active, int productCount) {}
    public record CategoryRequest(@NotBlank @Size(max = 120) String name, Long parentId, @Size(max = 1000) String description,
                                  String imageUrl, Integer sortOrder, Boolean active) {}

    private final JdbcTemplate jdbc;
    private final AuditService audit;
    public CategoryService(JdbcTemplate jdbc, AuditService audit) { this.jdbc = jdbc; this.audit = audit; }

    public List<CategoryView> list() {
        return jdbc.query("""
            SELECT c.id, c.parent_id, c.name, c.slug, c.description, c.image_url, c.sort_order, c.is_active,
                   (SELECT count(*) FROM products p WHERE p.category_id = c.id AND p.deleted_at IS NULL) AS pc
            FROM categories c WHERE c.deleted_at IS NULL ORDER BY c.parent_id NULLS FIRST, c.sort_order, c.name""",
            (rs, i) -> new CategoryView(rs.getLong("id"), (Long) rs.getObject("parent_id"), rs.getString("name"), rs.getString("slug"),
                    rs.getString("description"), rs.getString("image_url"), rs.getInt("sort_order"), rs.getBoolean("is_active"), rs.getInt("pc")));
    }

    @Transactional
    public long create(CategoryRequest r) {
        checkParent(r.parentId(), null);
        String slug = Slugs.unique(jdbc, "categories", r.name(), null);
        long id = jdbc.queryForObject("""
            INSERT INTO categories (parent_id, name, slug, description, image_url, sort_order, is_active) VALUES (?,?,?,?,?,?,?) RETURNING id""",
            Long.class, r.parentId(), r.name().trim(), slug, r.description(), Urls.clean(r.imageUrl()), r.sortOrder() == null ? 0 : r.sortOrder(), r.active() == null || r.active());
        audit.audit(CurrentUser.require().getId(), "CATEGORY_CREATED", "CATEGORY", id, null, r, null);
        return id;
    }

    @Transactional
    public void update(long id, CategoryRequest r) {
        requireExists(id);
        checkParent(r.parentId(), id);
        Integer children = jdbc.queryForObject("SELECT count(*) FROM categories WHERE parent_id = ? AND deleted_at IS NULL", Integer.class, id);
        if (r.parentId() != null && children != null && children > 0) throw bad("A category that has sub-categories must stay at the top level.");
        String slug = Slugs.unique(jdbc, "categories", r.name(), id);
        jdbc.update("""
            UPDATE categories SET parent_id=?, name=?, slug=?, description=?, image_url=?, sort_order=?, is_active=?, updated_at=now() WHERE id=?""",
            r.parentId(), r.name().trim(), slug, r.description(), Urls.clean(r.imageUrl()), r.sortOrder() == null ? 0 : r.sortOrder(), r.active() == null || r.active(), id);
        audit.audit(CurrentUser.require().getId(), "CATEGORY_UPDATED", "CATEGORY", id, null, r, null);
    }

    @Transactional
    public void delete(long id) {
        requireExists(id);
        Integer children = jdbc.queryForObject("SELECT count(*) FROM categories WHERE parent_id = ? AND deleted_at IS NULL", Integer.class, id);
        Integer products = jdbc.queryForObject("SELECT count(*) FROM products WHERE category_id = ? AND deleted_at IS NULL", Integer.class, id);
        if (children != null && children > 0) throw conflict("Remove or move its sub-categories first.");
        if (products != null && products > 0) throw conflict("This category still has products. Move or archive them first, or deactivate the category.");
        jdbc.update("UPDATE categories SET deleted_at = now(), updated_at = now() WHERE id = ?", id);
        audit.audit(CurrentUser.require().getId(), "CATEGORY_DELETED", "CATEGORY", id, null, null, null);
    }

    private void requireExists(long id) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM categories WHERE id = ? AND deleted_at IS NULL", Integer.class, id);
        if (n == null || n == 0) throw notFound("Category");
    }

    /** Two levels only: a parent must itself be top-level. This also rules out cycles. */
    private void checkParent(Long parentId, Long selfId) {
        if (parentId == null) return;
        if (parentId.equals(selfId)) throw bad("A category cannot be its own parent.");
        List<Long> p = jdbc.queryForList("SELECT parent_id FROM categories WHERE id = ? AND deleted_at IS NULL", Long.class, parentId);
        if (p.isEmpty()) throw bad("The chosen parent category does not exist.");
        if (p.get(0) != null) throw bad("Categories can only be nested one level deep (section → garment type).");
    }
}
