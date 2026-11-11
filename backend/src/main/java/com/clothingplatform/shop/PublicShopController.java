package com.clothingplatform.shop;

import com.clothingplatform.common.ApiResponse;
import com.clothingplatform.common.PageResult;
import com.clothingplatform.shop.ShopDtos.*;
import com.clothingplatform.shop.MeasurementService.FieldView;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.web.bind.annotation.*;

/** Everything here is public (no sign-in) and read-only. */
@RestController
@RequestMapping("/api/v1/public")
public class PublicShopController {
    private final PublicCatalogService catalog;
    private final DesignService designs;
    private final TrendService trends;
    private final MeasurementService measurements;

    public PublicShopController(PublicCatalogService catalog, DesignService designs, TrendService trends, MeasurementService measurements) {
        this.catalog = catalog; this.designs = designs; this.trends = trends; this.measurements = measurements;
    }

    @GetMapping("/home")
    public ApiResponse<HomeData> home() {
        return ApiResponse.ok(new HomeData(catalog.categoryTree(),
                catalog.search(null, null, null, null, null, null, null, true, null, "newest", 0, 8).items(),
                catalog.search(null, null, null, null, null, null, null, null, true, "newest", 0, 8).items(),
                trends.publicCollections(6)));
    }

    @GetMapping("/categories")
    public ApiResponse<List<CategoryNode>> categories() { return ApiResponse.ok(catalog.categoryTree()); }

    @GetMapping("/products")
    public ApiResponse<PageResult<ProductCard>> products(@RequestParam(required = false) String section, @RequestParam(required = false) String category,
                                                         @RequestParam(required = false) String q, @RequestParam(required = false) BigDecimal minPrice,
                                                         @RequestParam(required = false) BigDecimal maxPrice, @RequestParam(required = false) Long fabricId,
                                                         @RequestParam(required = false) String size, @RequestParam(required = false) Boolean featured,
                                                         @RequestParam(required = false) Boolean trending, @RequestParam(required = false) String sort,
                                                         @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "12") int pageSize) {
        return ApiResponse.ok(catalog.search(section, category, q, minPrice, maxPrice, fabricId, size, featured, trending, sort, page, pageSize));
    }

    @GetMapping("/products/{slug}")
    public ApiResponse<ProductPublic> product(@PathVariable String slug) { return ApiResponse.ok(catalog.bySlug(slug)); }

    /** Server-side price and validity for a customization; the browser never decides a price. */
    @PostMapping("/products/{id}/quote")
    public ApiResponse<Quote> quote(@PathVariable long id, @RequestBody DesignConfig config) { return ApiResponse.ok(designs.quoteResolved(catalog.byId(id), config, false)); }

    @GetMapping("/tailor-categories")
    public ApiResponse<List<TailorCategoryPublic>> tailorCategories() { return ApiResponse.ok(catalog.tailorCategories()); }

    @GetMapping("/trending")
    public ApiResponse<TrendingData> trending() { return ApiResponse.ok(trends.trending()); }

    @GetMapping("/measurement-fields")
    public ApiResponse<List<FieldView>> measurementFields() { return ApiResponse.ok(measurements.fields()); }
}
