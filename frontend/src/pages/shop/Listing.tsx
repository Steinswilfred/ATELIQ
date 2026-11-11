import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { GridSkeleton, ProductGrid } from '../../components/shop/ProductCard'
import { Button } from '../../components/ui/Button'
import { EmptyState } from '../../components/ui/EmptyState'
import { Pagination } from '../../components/ui/Pagination'
import { get } from '../../lib/api'
import type { CategoryNode, ProductCardT } from '../../lib/shopTypes'
import type { PageResult } from '../../lib/types'
import { useAsync } from '../../lib/useAsync'

const SORTS = [['newest', 'Newest'], ['price_asc', 'Price: low to high'], ['price_desc', 'Price: high to low'], ['name', 'Name']]
const field = 'rounded-lg border border-ink/20 bg-canvas px-3 py-2 text-sm'

/** Men / Women / Kids / all products. All filters live in the URL so results can be shared and the back button works. */
export default function Listing({ section }: { section?: string }) {
  const [params, setParams] = useSearchParams()
  const [q, setQ] = useState(params.get('q') ?? '')
  const [min, setMin] = useState(params.get('minPrice') ?? '')
  const [max, setMax] = useState(params.get('maxPrice') ?? '')
  const category = params.get('category') ?? ''
  const sort = params.get('sort') ?? 'newest'
  const page = Number(params.get('page') ?? 0)
  const featured = params.get('featured') === 'true'

  useEffect(() => { setQ(params.get('q') ?? '') }, [params])
  const tree = useAsync(() => get<CategoryNode[]>('/public/categories').catch(() => [] as CategoryNode[]), [])
  const current = tree.data?.find((s) => s.slug === section)
  const query = new URLSearchParams({ page: String(page), pageSize: '12', sort })
  if (section) query.set('section', section)
  if (category) query.set('category', category)
  if (params.get('q')) query.set('q', params.get('q')!)
  if (params.get('minPrice')) query.set('minPrice', params.get('minPrice')!)
  if (params.get('maxPrice')) query.set('maxPrice', params.get('maxPrice')!)
  if (featured) query.set('featured', 'true')
  const { data, loading, error } = useAsync(() => get<PageResult<ProductCardT>>(`/public/products?${query}`), [query.toString()])

  const set = (k: string, v: string) => { const n = new URLSearchParams(params); v ? n.set(k, v) : n.delete(k); if (k !== 'page') n.delete('page'); setParams(n) }
  const apply = (e: React.FormEvent) => { e.preventDefault(); const n = new URLSearchParams(params); [['q', q], ['minPrice', min], ['maxPrice', max]].forEach(([k, v]) => v ? n.set(k, v) : n.delete(k)); n.delete('page'); setParams(n) }
  const title = current?.name ?? (section ? section[0].toUpperCase() + section.slice(1) : featured ? 'Featured' : 'All styles')

  return (
    <div className="mx-auto max-w-7xl px-4 py-10">
      <h1 className="text-4xl font-bold">{title}</h1>
      {current && current.children.length > 0 && (
        <div className="mt-4 flex flex-wrap gap-2" role="group" aria-label="Garment types">
          <Button variant={category ? 'ghost' : 'dark'} onClick={() => set('category', '')}>All</Button>
          {current.children.map((c) => <Button key={c.id} variant={category === c.slug ? 'dark' : 'ghost'} onClick={() => set('category', c.slug)}>{c.name}</Button>)}
        </div>)}
      <form onSubmit={apply} className="mt-6 flex flex-wrap items-end gap-3">
        <input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Search styles" aria-label="Search" className={`${field} w-56`} />
        <input value={min} onChange={(e) => setMin(e.target.value)} inputMode="decimal" placeholder="Min ₹" aria-label="Minimum price" className={`${field} w-24`} />
        <input value={max} onChange={(e) => setMax(e.target.value)} inputMode="decimal" placeholder="Max ₹" aria-label="Maximum price" className={`${field} w-24`} />
        <Button type="submit" variant="dark">Apply</Button>
        <label className="ml-auto text-sm">Sort <select value={sort} onChange={(e) => set('sort', e.target.value)} className={field}>{SORTS.map(([v, l]) => <option key={v} value={v}>{l}</option>)}</select></label>
      </form>
      <div className="mt-8">
        {error ? <EmptyState title="Could not load products" hint={error.message} />
          : loading ? <GridSkeleton />
          : data!.items.length === 0 ? <EmptyState title="No styles match" hint="Try removing a filter or searching for something else." action={<Button variant="ghost" onClick={() => setParams({})}>Clear filters</Button>} />
          : <><ProductGrid items={data!.items} /><Pagination page={data!.page} size={data!.size} total={data!.total} onPage={(p) => set('page', String(p))} /></>}
      </div>
    </div>
  )
}
