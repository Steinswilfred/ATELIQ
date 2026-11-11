import { useState } from 'react'
import { Button } from '../../components/ui/Button'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'
import { EmptyState } from '../../components/ui/EmptyState'
import { Field, FormDialog, Values } from '../../components/ui/FormDialog'
import { Skeleton } from '../../components/ui/Skeleton'
import { ApiError, get, http, post } from '../../lib/api'
import { useAsync } from '../../lib/useAsync'
import { toast } from '../../store/toastStore'

interface Fabric { id: number; name: string; material?: string; description?: string; priceDelta: number; imageUrl?: string; active: boolean; colorIds: number[] }
interface Color { id: number; name: string; hexCode: string; active: boolean }
interface Size { id: number; code: string; name: string; sortOrder: number; active: boolean }
type Tab = 'fabrics' | 'colors' | 'sizes'
const money = (n: number) => `₹${Number(n).toFixed(2)}`

export default function AdminAttributes() {
  const [tab, setTab] = useState<Tab>('fabrics')
  const fabrics = useAsync(() => get<Fabric[]>('/admin/fabrics'), [])
  const colors = useAsync(() => get<Color[]>('/admin/colors'), [])
  const sizes = useAsync(() => get<Size[]>('/admin/sizes'), [])
  const [edit, setEdit] = useState<{ open: boolean; item?: Fabric | Color | Size }>({ open: false })
  const [del, setDel] = useState<{ id: number; name: string } | null>(null)
  const reloadAll = () => { fabrics.reload(); colors.reload(); sizes.reload() }

  const spec: Record<Tab, { fields: Field[]; initial: (i?: never) => Values; toBody: (v: Values) => unknown }> = {
    fabrics: {
      fields: [{ name: 'name', label: 'Name', required: true }, { name: 'material', label: 'Material', hint: 'e.g. 100% cotton' }, { name: 'description', label: 'Description', type: 'textarea' },
        { name: 'priceDelta', label: 'Extra price (₹) added to the product price', type: 'number', step: '0.01', required: true }, { name: 'imageUrl', label: 'Swatch image', type: 'image' },
        { name: 'colorIds', label: 'Colours available in this fabric', type: 'multicheck', options: (colors.data ?? []).map((c) => ({ value: String(c.id), label: c.name })) }, { name: 'active', label: 'Available', type: 'checkbox' }],
      initial: () => { const f = edit.item as Fabric | undefined; return f ? { name: f.name, material: f.material ?? '', description: f.description ?? '', priceDelta: String(f.priceDelta), imageUrl: f.imageUrl ?? '', colorIds: f.colorIds.map(String), active: f.active } : { name: '', material: '', description: '', priceDelta: '0', imageUrl: '', colorIds: [], active: true } },
      toBody: (v) => ({ name: v.name, material: v.material || null, description: v.description || null, priceDelta: Number(v.priceDelta), imageUrl: v.imageUrl || null, colorIds: (v.colorIds as string[]).map(Number), active: v.active }),
    },
    colors: {
      fields: [{ name: 'name', label: 'Name', required: true }, { name: 'hexCode', label: 'Colour', type: 'color', required: true }, { name: 'active', label: 'Available', type: 'checkbox' }],
      initial: () => { const c = edit.item as Color | undefined; return c ? { name: c.name, hexCode: c.hexCode, active: c.active } : { name: '', hexCode: '#E8505B', active: true } },
      toBody: (v) => ({ name: v.name, hexCode: v.hexCode, active: v.active }),
    },
    sizes: {
      fields: [{ name: 'code', label: 'Code', required: true, hint: 'Capital letters, e.g. XL' }, { name: 'name', label: 'Name', required: true }, { name: 'sortOrder', label: 'Sort order', type: 'number' }, { name: 'active', label: 'Available', type: 'checkbox' }],
      initial: () => { const s = edit.item as Size | undefined; return s ? { code: s.code, name: s.name, sortOrder: String(s.sortOrder), active: s.active } : { code: '', name: '', sortOrder: '0', active: true } },
      toBody: (v) => ({ code: String(v.code).toUpperCase(), name: v.name, sortOrder: Number(v.sortOrder || 0), active: v.active }),
    },
  }
  const save = async (v: Values) => {
    const base = `/admin/${tab}`
    const body = spec[tab].toBody(v)
    if (edit.item) await http.put(`${base}/${edit.item.id}`, body); else await post(base, body)
    toast.success('Saved.'); setEdit({ open: false }); reloadAll()
  }
  const remove = async () => {
    try { await http.delete(`/admin/${tab}/${del!.id}`); toast.success('Deleted.'); reloadAll() } catch (e) { toast.error((e as ApiError).message) }
    setDel(null)
  }
  const cur = tab === 'fabrics' ? fabrics : tab === 'colors' ? colors : sizes
  const Actions = ({ item }: { item: Fabric | Color | Size }) => <td className="space-x-3 p-3 text-right font-semibold"><button className="text-primary" onClick={() => setEdit({ open: true, item })}>Edit</button>
    <button className="text-ink/70 hover:text-primary" onClick={() => setDel({ id: item.id, name: 'name' in item ? item.name : '' })}>Delete</button></td>

  return (
    <div>
      <div className="flex flex-wrap items-center justify-between gap-3"><h1 className="text-3xl font-bold">Fabrics, colours & sizes</h1><Button onClick={() => setEdit({ open: true })}>Add {tab === 'fabrics' ? 'fabric' : tab === 'colors' ? 'colour' : 'size'}</Button></div>
      <div className="mt-4 flex gap-2" role="tablist">{(['fabrics', 'colors', 'sizes'] as Tab[]).map((t) => <Button key={t} variant={tab === t ? 'dark' : 'ghost'} role="tab" aria-selected={tab === t} onClick={() => setTab(t)}>{t === 'colors' ? 'Colours' : t[0].toUpperCase() + t.slice(1)}</Button>)}</div>
      <div className="mt-4 overflow-x-auto rounded-xl bg-canvas ring-1 ring-ink/10">
        {cur.error ? <EmptyState title="Could not load" hint={cur.error.message} /> : cur.loading ? <div className="space-y-2 p-4"><Skeleton className="h-8" /><Skeleton className="h-8" /></div> : (cur.data as unknown[]).length === 0 ? <EmptyState title="Nothing here yet" hint="Add the first one with the button above." /> : (
          <table className="w-full text-left text-sm">
            {tab === 'fabrics' && <><thead className="border-b border-ink/10 text-xs text-body/70"><tr><th className="p-3">Fabric</th><th>Extra price</th><th>Colours</th><th>Status</th><th /></tr></thead>
              <tbody>{fabrics.data!.map((f) => <tr key={f.id} className="border-b border-ink/5"><td className="p-3"><div className="flex items-center gap-3">{f.imageUrl && <img src={f.imageUrl} alt="" className="h-9 w-9 rounded object-cover" />}<div><div className="font-semibold text-ink">{f.name}</div><div className="text-xs">{f.material}</div></div></div></td>
                <td>{money(f.priceDelta)}</td><td>{f.colorIds.length}</td><td>{f.active ? 'Available' : 'Hidden'}</td><Actions item={f} /></tr>)}</tbody></>}
            {tab === 'colors' && <><thead className="border-b border-ink/10 text-xs text-body/70"><tr><th className="p-3">Colour</th><th>Code</th><th>Status</th><th /></tr></thead>
              <tbody>{colors.data!.map((c) => <tr key={c.id} className="border-b border-ink/5"><td className="p-3"><span className="mr-2 inline-block h-4 w-4 rounded-full align-middle ring-1 ring-ink/20" style={{ background: c.hexCode }} />{c.name}</td><td>{c.hexCode}</td><td>{c.active ? 'Available' : 'Hidden'}</td><Actions item={c} /></tr>)}</tbody></>}
            {tab === 'sizes' && <><thead className="border-b border-ink/10 text-xs text-body/70"><tr><th className="p-3">Code</th><th>Name</th><th>Order</th><th>Status</th><th /></tr></thead>
              <tbody>{sizes.data!.map((s) => <tr key={s.id} className="border-b border-ink/5"><td className="p-3 font-semibold text-ink">{s.code}</td><td>{s.name}</td><td>{s.sortOrder}</td><td>{s.active ? 'Available' : 'Hidden'}</td><Actions item={s} /></tr>)}</tbody></>}
          </table>)}
      </div>
      <p className="mt-3 text-xs">Colours and sizes that are in use cannot be deleted; hide them instead. Custom measurements are handled in the customer measurement profiles (Phase 5).</p>
      <FormDialog open={edit.open} title={`${edit.item ? 'Edit' : 'New'} ${tab === 'fabrics' ? 'fabric' : tab === 'colors' ? 'colour' : 'size'}`} fields={spec[tab].fields} initial={spec[tab].initial()} onSubmit={save} onCancel={() => setEdit({ open: false })} />
      <ConfirmDialog open={!!del} danger title={`Delete “${del?.name}”?`} message="This cannot be undone. Items that are still in use cannot be deleted." confirmLabel="Delete" onConfirm={remove} onCancel={() => setDel(null)} />
    </div>
  )
}
