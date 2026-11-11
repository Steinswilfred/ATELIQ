import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Button } from '../../components/ui/Button'
import { EmptyState } from '../../components/ui/EmptyState'
import { FormDialog, Values } from '../../components/ui/FormDialog'
import { Pagination } from '../../components/ui/Pagination'
import { Skeleton } from '../../components/ui/Skeleton'
import { StatusBadge } from '../../components/ui/StatusBadge'
import { get, post } from '../../lib/api'
import type { PageResult } from '../../lib/types'
import { useAsync } from '../../lib/useAsync'
import { useAuth } from '../../store/authStore'
import { toast } from '../../store/toastStore'
import type { Category } from './AdminCategories'
import { selectClass } from './AdminUsers'

export interface ProductRow { id: number; name: string; slug: string; categoryId: number; categoryName: string; basePrice: number; status: string; featured: boolean; trending: boolean; primaryImage?: string }

export function categoryOptions(cats: Category[]) {
  const roots = cats.filter((c) => c.parentId === null)
  return roots.flatMap((r) => [{ value: String(r.id), label: r.name }, ...cats.filter((c) => c.parentId === r.id).map((c) => ({ value: String(c.id), label: `${r.name} › ${c.name}` }))])
}

export default function AdminProducts() {
  const can = useAuth((s) => s.can)
  const navigate = useNavigate()
  const [page, setPage] = useState(0)
  const [q, setQ] = useState('')
  const [query, setQuery] = useState('')
  const [status, setStatus] = useState('')
  const [categoryId, setCategoryId] = useState('')
  const [open, setOpen] = useState(false)
  const cats = useAsync(() => get<Category[]>('/admin/categories').catch(() => [] as Category[]), [])
  const { data, loading, error } = useAsync(() => get<PageResult<ProductRow>>(`/admin/products?page=${page}&size=20&status=${status}&categoryId=${categoryId}&q=${encodeURIComponent(query)}`), [page, status, categoryId, query])
  const options = categoryOptions(cats.data ?? [])

  const create = async (v: Values) => {
    const r = await post<{ id: number }>('/admin/products', { categoryId: Number(v.categoryId), name: v.name, description: v.description || null, basePrice: Number(v.basePrice) })
    toast.success('Product created as a draft. Add images, sizes and options next.'); setOpen(false); navigate(`/admin/products/${r.id}`)
  }

  return (
    <div>
      <div className="flex flex-wrap items-center justify-between gap-3"><h1 className="text-3xl font-bold">Products</h1>{can('PRODUCT_WRITE') && <Button onClick={() => setOpen(true)}>New product</Button>}</div>
      <form className="mt-4 flex flex-wrap gap-2" onSubmit={(e) => { e.preventDefault(); setQuery(q); setPage(0) }}>
        <input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Search products" aria-label="Search products" className={`${selectClass} w-56`} />
        <select value={categoryId} onChange={(e) => { setCategoryId(e.target.value); setPage(0) }} className={selectClass} aria-label="Category"><option value="">All categories</option>{options.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}</select>
        <select value={status} onChange={(e) => { setStatus(e.target.value); setPage(0) }} className={selectClass} aria-label="Status"><option value="">All statuses</option><option>DRAFT</option><option>ACTIVE</option><option>ARCHIVED</option></select>
        <Button type="submit" variant="dark">Search</Button>
      </form>
      <div className="mt-4 overflow-x-auto rounded-xl bg-canvas ring-1 ring-ink/10">
        {error ? <EmptyState title="Could not load products" hint={error.message} /> : loading ? <div className="space-y-2 p-4">{Array.from({ length: 5 }, (_, i) => <Skeleton key={i} className="h-10" />)}</div>
          : data!.items.length === 0 ? <EmptyState title="No products yet" hint="Create your first product, then attach images, sizes, fabrics and customization options." /> : (
          <table className="w-full text-left text-sm"><thead className="border-b border-ink/10 text-xs text-body/70"><tr><th className="p-3">Product</th><th>Category</th><th>Base price</th><th>Status</th><th>Flags</th></tr></thead>
            <tbody>{data!.items.map((p) => (
              <tr key={p.id} className="border-b border-ink/5 hover:bg-surface"><td className="p-3"><div className="flex items-center gap-3">{p.primaryImage ? <img src={p.primaryImage} alt="" className="h-10 w-10 rounded object-cover" /> : <div className="h-10 w-10 rounded bg-surface" />}
                <Link to={`/admin/products/${p.id}`} className="font-semibold text-ink hover:text-primary">{p.name}</Link></div></td>
                <td>{p.categoryName}</td><td>₹{Number(p.basePrice).toFixed(2)}</td><td><StatusBadge status={p.status} /></td><td className="text-xs">{[p.featured && 'Featured', p.trending && 'Trending'].filter(Boolean).join(' · ') || '—'}</td></tr>))}</tbody></table>)}
      </div>
      {data && <Pagination page={data.page} size={data.size} total={data.total} onPage={setPage} />}
      <FormDialog open={open} title="New product" submitLabel="Create draft" onSubmit={create} onCancel={() => setOpen(false)} initial={{ categoryId: options[0]?.value ?? '', name: '', basePrice: '', description: '' }}
        fields={[{ name: 'categoryId', label: 'Category', type: 'select', required: true, options }, { name: 'name', label: 'Name', required: true }, { name: 'basePrice', label: 'Base price (₹)', type: 'number', step: '0.01', required: true, hint: 'Tailor stitching charges are added at checkout.' }, { name: 'description', label: 'Description', type: 'textarea' }]} />
    </div>
  )
}
