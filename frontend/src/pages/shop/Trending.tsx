import { GridSkeleton, ProductGrid } from '../../components/shop/ProductCard'
import { EmptyState } from '../../components/ui/EmptyState'
import { get } from '../../lib/api'
import type { CollectionT, ProductCardT } from '../../lib/shopTypes'
import { useAsync } from '../../lib/useAsync'

export default function Trending() {
  const { data, loading, error } = useAsync(() => get<{ collections: CollectionT[]; trendingProducts: ProductCardT[] }>('/public/trending'), [])
  return (
    <div className="mx-auto max-w-7xl px-4 py-10">
      <h1 className="text-4xl font-bold">Trending collections</h1>
      <p className="mt-2 max-w-2xl text-sm">Collections chosen by our styling team. They are curated by hand, not a live feed of global fashion data.</p>
      {error ? <div className="mt-8"><EmptyState title="Could not load collections" hint={error.message} /></div> : loading ? <div className="mt-8"><GridSkeleton n={4} /></div> : (<>
        {data!.collections.map((c) => (
          <section key={c.id} className="mt-12">
            <div className="mb-4"><span className="text-xs font-bold uppercase text-primary">{c.kind.toLowerCase()}</span><h2 className="text-2xl font-bold">{c.name}</h2>{c.description && <p className="mt-1 max-w-2xl text-sm">{c.description}</p>}</div>
            <ProductGrid items={c.products} />
          </section>))}
        {data!.trendingProducts.length > 0 && <section className="mt-12"><h2 className="mb-4 text-2xl font-bold">Trending styles</h2><ProductGrid items={data!.trendingProducts} /></section>}
        {data!.collections.length === 0 && data!.trendingProducts.length === 0 && <div className="mt-8"><EmptyState title="No trending collections yet" hint="Check back soon." /></div>}
      </>)}
    </div>
  )
}
