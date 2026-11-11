export interface ProductCardT { id: number; name: string; slug: string; basePrice: number; categoryName: string; imageUrl?: string; featured: boolean; trending: boolean }
export interface CategoryNode { id: number; name: string; slug: string; imageUrl?: string; productCount: number; children: CategoryNode[] }
export interface ColorT { id: number; name: string; hexCode: string }
export interface FabricT { id: number; name: string; material?: string; description?: string; priceDelta: number; imageUrl?: string; colors: ColorT[] }
export interface OptionT { id: number; code: string; name: string; description?: string; imageUrl?: string; priceDelta: number; isDefault: boolean; renderBinding?: { type: string; target: string; value?: string } | null }
export interface GroupT { id: number; code: string; name: string; description?: string; required: boolean; options: OptionT[] }
export interface ProductT {
  id: number; name: string; slug: string; description?: string; basePrice: number; categoryName: string; sectionName: string; modelUrl?: string
  images: { url: string; alt?: string }[]; sizes: { code: string; name: string }[]; fabrics: FabricT[]; groups: GroupT[]
}
export interface DesignConfig { sizeCode: string | null; measurementProfileId: number | null; fabricId: number | null; colorId: number | null; options: Record<string, number>; tailorCategoryId?: number | null }
export interface TailorCategoryT { id: number; name: string; description?: string; level: number; stitchingPrice: number }
export interface Quote { valid: boolean; errors: string[]; basePrice: number; lines: { label: string; amount: number }[]; total: number }
export interface CollectionT { id: number; name: string; slug: string; kind: string; description?: string; imageUrl?: string; products: ProductCardT[] }
export interface MeasurementField { code: string; label: string; instruction: string; minCm: number; maxCm: number }
export interface MeasurementProfile { id: number; name: string; unit: 'CM' | 'IN'; measurements: Record<string, number>; isDefault: boolean; updatedAt: string }
export interface DesignSummary { id: number; productId: number; productName: string; productSlug: string; imageUrl?: string; available: boolean; name: string; configuration: DesignConfig; updatedAt: string }
