import { AnimatePresence, motion } from 'framer-motion'
import { useMemo, useState } from 'react'
import { Button } from '../../components/ui/Button'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'
import { EmptyState } from '../../components/ui/EmptyState'
import { Field, FormDialog, Values } from '../../components/ui/FormDialog'
import { Skeleton } from '../../components/ui/Skeleton'
import { ApiError, get, http, post } from '../../lib/api'
import type { PageResult } from '../../lib/types'
import { useAsync } from '../../lib/useAsync'
import { useAuth } from '../../store/authStore'
import { toast } from '../../store/toastStore'
import type { ProductRow } from './AdminProducts'

interface Collection { id: number; name: string; slug: string; kind: string; description?: string; imageUrl?: string; sortOrder: number; active: boolean; source: string; productIds: number[] }
const KINDS = ['SEASONAL', 'CASUAL', 'FORMAL', 'TRADITIONAL', 'WEDDING', 'FESTIVAL', 'MEN', 'WOMEN', 'KIDS', 'OTHER']

function ProductPicker({ collection, products, onClose, onSaved }: { collection: Collection; products: ProductRow[]; onClose: () => void; onSaved: () => void }) {
  const [ids, setIds] = useState<number[]>(collection.productIds)
  const [q, setQ] = useState('')
  const [busy, setBusy] = useState(false)
  const shown = useMemo(() => products.filter((p) => p.name.toLowerCase().includes(q.toLowerCase())), [products, q])
  const save = async () => { setBusy(true); try { await http.put(`/admin/collections/${collection.id}/products`, { ids }); toast.success('Products saved.'); onSaved() } catch (e) { toast.error((e as ApiError).message) } finally { setBusy(false) } }
  return (
    <motion.div className="fixed inset-0 z-40 grid place-items-center bg-ink/50 p-4" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} onClick={onClose} role="dialog" aria-modal="true" aria-label="Choose products">
      <div className="w-full max-w-lg rounded-2xl bg-canvas p-6 shadow-xl" onClick={(e) => e.stopPropagation()}>
        <h2 className="text-xl font-bold">Products in “{collection.name}”</h2><p className="text-xs">Only published products are shown to customers.</p>
        <input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Filter products" aria-label="Filter products" className="mt-3 w-full rounded-lg border border-ink/20 px-3 py-2 text-sm" />
        <ul className="mt-3 max-h-72 space-y-1 overflow-auto">{shown.length === 0 && <li className="text-sm">No products found.</li>}{shown.map((p) => (
          <li key={p.id}><label className="flex items-center gap-2 text-sm"><input type="checkbox" className="accent-primary" checked={ids.includes(p.id)} onChange={() => setIds((x) => x.includes(p.id) ? x.filter((i) => i !== p.id) : [...x, p.id])} />{p.name} <span className="text-xs">({p.status.toLowerCase()})</span></label></li>))}</ul>
        <div className="mt-5 flex items-center justify-between"><span className="text-xs">{ids.length} selected</span><div className="flex gap-2"><Button variant="ghost" onClick={onClose}>Cancel</Button><Button loading={busy} onClick={save}>Save products</Button></div></div>
      </div>
    </motion.div>
  )
}

export default function AdminCollections() {
  const can = useAuth((s) => s.can)
  const writable = can('PRODUCT_WRITE')
  const { data, loading, error, reload } = useAsync(() => get<Collection[]>('/admin/collections'), [])
  const products = useAsync(() => get<PageResult<ProductRow>>('/admin/products?size=100').then((r) => r.items), [])
  const [edit, setEdit] = useState<{ open: boolean; c?: Collection }>({ open: false })
  const [pick, setPick] = useState<Collection | null>(null)
  const [del, setDel] = useState<Collection | null>(null)

  const fields: Field[] = [{ name: 'name', label: 'Name', required: true }, { name: 'kind', label: 'Type', type: 'select', options: KINDS.map((k) => ({ value: k, label: k[0] + k.slice(1).toLowerCase() })) },
    { name: 'description', label: 'Description', type: 'textarea' }, { name: 'imageUrl', label: 'Cover image', type: 'image' }, { name: 'sortOrder', label: 'Sort order', type: 'number' }, { name: 'active', label: 'Shown to customers', type: 'checkbox' }]
  const c = edit.c
  const initial: Values = c ? { name: c.name, kind: c.kind, description: c.description ?? '', imageUrl: c.imageUrl ?? '', sortOrder: String(c.sortOrder), active: c.active } : { name: '', kind: 'SEASONAL', description: '', imageUrl: '', sortOrder: '0', active: true }
  const save = async (v: Values) => {
    const body = { name: v.name, kind: v.kind, description: v.description || null, imageUrl: v.imageUrl || null, sortOrder: Number(v.sortOrder || 0), active: v.active }
    if (c) await http.put(`/admin/collections/${c.id}`, body); else await post('/admin/collections', body)
    toast.success('Collection saved.'); setEdit({ open: false }); reload()
  }
  const remove = async () => { try { await http.delete(`/admin/collections/${del!.id}`); toast.success('Collection deleted.'); reload() } catch (e) { toast.error((e as ApiError).message) } setDel(null) }

  if (error) return <EmptyState title="Could not load collections" hint={error.message} />
  return (
    <div>
      <div className="flex flex-wrap items-center justify-between gap-3"><h1 className="text-3xl font-bold">Trend collections</h1>{writable && <Button onClick={() => setEdit({ open: true })}>New collection</Button>}</div>
      <p className="mt-1 text-sm">Curate what the Trending page shows: seasonal, wedding, festival, casual, formal and traditional collections. To mark a single product as trending or featured, use its edit page. Collections without published products stay hidden.</p>
      <div className="mt-6 space-y-3">
        {loading ? <Skeleton className="h-32" /> : data!.length === 0 ? <EmptyState title="No collections yet" hint="Create one, then choose the products in it." /> : data!.map((col) => (
          <section key={col.id} className="flex flex-wrap items-center justify-between gap-3 rounded-xl bg-canvas p-5 ring-1 ring-ink/10">
            <div className="flex items-center gap-3">{col.imageUrl && <img src={col.imageUrl} alt="" className="h-12 w-12 rounded object-cover" />}
              <div><h2 className="font-bold text-ink">{col.name}{!col.active && <span className="ml-2 text-xs font-normal">(hidden)</span>}</h2><p className="text-xs">{col.kind.toLowerCase()} · {col.productIds.length} product{col.productIds.length === 1 ? '' : 's'} · source: {col.source.toLowerCase()}</p></div></div>
            {writable && <div className="flex gap-3 text-sm font-semibold"><button className="text-primary" onClick={() => setPick(col)}>Products</button><button className="text-primary" onClick={() => setEdit({ open: true, c: col })}>Edit</button><button className="text-ink/70 hover:text-primary" onClick={() => setDel(col)}>Delete</button></div>}
          </section>))}
      </div>
      <FormDialog open={edit.open} title={c ? 'Edit collection' : 'New collection'} fields={fields} initial={initial} onSubmit={save} onCancel={() => setEdit({ open: false })} />
      <AnimatePresence>{pick && products.data && <ProductPicker collection={pick} products={products.data} onClose={() => setPick(null)} onSaved={() => { setPick(null); reload() }} />}</AnimatePresence>
      <ConfirmDialog open={!!del} danger title={`Delete “${del?.name}”?`} message="The products themselves are not affected." confirmLabel="Delete collection" onConfirm={remove} onCancel={() => setDel(null)} />
    </div>
  )
}
