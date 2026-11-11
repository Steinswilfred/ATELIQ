package com.clothingplatform.shop;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public final class ShopDtos {
    private ShopDtos() {}

    public record CategoryNode(long id, String name, String slug, String imageUrl, int productCount, List<CategoryNode> children) {}
    public record ProductCard(long id, String name, String slug, BigDecimal basePrice, String categoryName, String imageUrl, boolean featured, boolean trending) {}

    public record ColorPublic(long id, String name, String hexCode) {}
    public record FabricPublic(long id, String name, String material, String description, BigDecimal priceDelta, String imageUrl, List<ColorPublic> colors) {}
    public record SizePublic(String code, String name) {}
    public record OptionPublic(long id, String code, String name, String description, String imageUrl, BigDecimal priceDelta, boolean isDefault, JsonNode renderBinding) {}
    public record GroupPublic(long id, String code, String name, String description, boolean required, List<OptionPublic> options) {}
    public record ImagePublic(String url, String alt) {}
    public record ProductPublic(long id, String name, String slug, String description, BigDecimal basePrice, String categoryName, String sectionName,
                                String modelUrl, List<ImagePublic> images, List<SizePublic> sizes, List<FabricPublic> fabrics, List<GroupPublic> groups) {}

    /** What a customer chose. Option keys are customization group ids. */
    public record DesignConfig(String sizeCode, Long measurementProfileId, Long fabricId, Long colorId, Map<Long, Long> options, Long tailorCategoryId) {
        public DesignConfig(String sizeCode, Long measurementProfileId, Long fabricId, Long colorId, Map<Long, Long> options) { this(sizeCode, measurementProfileId, fabricId, colorId, options, null); }
    }
    public record TailorCategoryPublic(long id, String name, String description, int level, BigDecimal stitchingPrice) {}
    public record PriceLine(String label, BigDecimal amount) {}
    public record Quote(boolean valid, List<String> errors, BigDecimal basePrice, List<PriceLine> lines, BigDecimal total) {}

    public record CollectionPublic(long id, String name, String slug, String kind, String description, String imageUrl, List<ProductCard> products) {}
    public record HomeData(List<CategoryNode> sections, List<ProductCard> featured, List<ProductCard> trending, List<CollectionPublic> collections) {}
    public record TrendingData(List<CollectionPublic> collections, List<ProductCard> trendingProducts) {}
}
