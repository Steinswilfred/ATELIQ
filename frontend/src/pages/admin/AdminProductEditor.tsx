import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { Button } from '../../components/ui/Button'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'
import { EmptyState } from '../../components/ui/EmptyState'
import { ImageUploader } from '../../components/ui/ImageUploader'
import { Skeleton } from '../../components/ui/Skeleton'
import { StatusBadge } from '../../components/ui/StatusBadge'
import { ApiError, get, http, post } from '../../lib/api'
import { useAsync } from '../../lib/useAsync'
import { useAuth } from '../../store/authStore'
import { toast } from '../../store/toastStore'
import type { Category } from './AdminCategories'
import type { Group } from './AdminCustomization'
import { categoryOptions } from './AdminProducts'

interface Detail {
  summary: { id: number; name: string; categoryId: number; basePrice: number; status: string; featured: boolean; trending: boolean }
  description?: string; modelUrl?: string
  images: { id: number; url: string; alt?: string; primary: boolean }[]
  sizeIds: number[]; fabricIds: number[]
  groups: { groupId: number; required: boolean }[]; options: { optionId: number; isDefault: boolean }[]
}
interface Size { id: number; code: string; name: string; active: boolean }
interface Fabric { id: number; name: string; active: boolean }
const input = 'w-full rounded-lg border border-ink/20 bg-canvas px-3 py-2 text-sm focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/30'
function ModelUploader({ value, onChange }: { value: string; onChange: (u: string) => void }) {
  const [busy, setBusy] = useState(false)
  const pick = async (file?: File) => {
    if (!file) return
    if (!file.name.toLowerCase().endsWith('.glb')) { toast.error('Please select a .glb file.'); return }
    if (file.size > 20 * 1024 * 1024) { toast.error('GLB must be 20 MB or smaller. Optimize the model in Blender before uploading.'); return }
    const fd = new FormData(); fd.append('file', file)
    setBusy(true)
    try { const r = await http.post<{ data: { url: string } }>('/admin/uploads/models', fd, { headers: { 'Content-Type': 'multipart/form-data' } }); onChange(r.data.data.url); toast.success('Model uploaded. Save details to keep it.') }
    catch (e) { toast.error((e as ApiError).message) } finally { setBusy(false) }
  }
  return <label className="inline-flex cursor-pointer items-center gap-2 text-sm font-semibold text-primary"><input type="file" accept=".glb,model/gltf-binary" className="sr-only" aria-label="Upload .glb model" onChange={(e) => void pick(e.target.files?.[0])} />{busy ? 'Uploading…' : value ? `Replace model (${value.split('/').pop()})` : 'Upload .glb'}</label>
}
const Card = ({ title, children, note }: { title: string; children: React.ReactNode; note?: string }) => <section className="rounded-xl bg-canvas p-5 ring-1 ring-ink/10"><h2 className="text-lg font-bold">{title}</h2>{note && <p className="text-xs">{note}</p>}<div className="mt-4">{children}</div></section>

export default function AdminProductEditor() {
  const { id } = useParams()
  const navigate = useNavigate()
  const can = useAuth((s) => s.can)
  const writable = can('PRODUCT_WRITE')
  const prod = useAsync(() => get<Detail>(`/admin/products/${id}`), [id])
  const cats = useAsync(() => get<Category[]>('/admin/categories').catch(() => [] as Category[]), [])
  const sizes = useAsync(() => get<Size[]>('/admin/sizes').catch(() => [] as Size[]), [])
  const fabrics = useAsync(() => get<Fabric[]>('/admin/fabrics').catch(() => [] as Fabric[]), [])
  const groups = useAsync(() => get<Group[]>('/admin/customization/groups').catch(() => [] as Group[]), [])

  const [form, setForm] = useState({ name: '', categoryId: '', basePrice: '', description: '', modelUrl: '', featured: false, trending: false })
  const [sizeIds, setSizeIds] = useState<number[]>([])
  const [fabricIds, setFabricIds] = useState<number[]>([])
  const [offered, setOffered] = useState<Record<number, { required: boolean }>>({})
  const [chosen, setChosen] = useState<Record<number, { isDefault: boolean }>>({})
  const [confirmDelete, setConfirmDelete] = useState(false)
  const [busy, setBusy] = useState<string | null>(null)

  useEffect(() => {
    const d = prod.data; if (!d) return
    setForm({ name: d.summary.name, categoryId: String(d.summary.categoryId), basePrice: String(d.summary.basePrice), description: d.description ?? '', modelUrl: d.modelUrl ?? '', featured: d.summary.featured, trending: d.summary.trending })
    setSizeIds(d.sizeIds); setFabricIds(d.fabricIds)
    setOffered(Object.fromEntries(d.groups.map((g) => [g.groupId, { required: g.required }])))
    setChosen(Object.fromEntries(d.options.map((o) => [o.optionId, { isDefault: o.isDefault }])))
  }, [prod.data])

  const run = async (key: string, fn: () => Promise<unknown>, ok: string) => {
    setBusy(key)
    try { await fn(); toast.success(ok); prod.reload(); return true } catch (e) { const err = e as ApiError; toast.error(err.details ? Object.values(err.details)[0] : err.message); return false } finally { setBusy(null) }
  }
  const toggle = (list: number[], set: (x: number[]) => void, v: number) => set(list.includes(v) ? list.filter((x) => x !== v) : [...list, v])

  const saveCustomization = () => {
    const groupsPayload = (groups.data ?? []).filter((g) => offered[g.id]).map((g) => ({ groupId: g.id, required: offered[g.id].required }))
    const optionsPayload = (groups.data ?? []).filter((g) => offered[g.id]).flatMap((g) => g.options.filter((o) => chosen[o.id]).map((o) => ({ optionId: o.id, isDefault: chosen[o.id].isDefault })))
    return run('cust', () => http.put(`/admin/products/${id}/customization`, { groups: groupsPayload, options: optionsPayload }), 'Customization saved.')
  }

  if (prod.loading) return <div className="space-y-3"><Skeleton className="h-10 w-1/3" /><Skeleton className="h-64" /></div>
  if (prod.error || !prod.data) return <EmptyState title="Could not load this product" hint={prod.error?.message} />
  const d = prod.data
  const setDefault = (groupOptions: number[], optionId: number) => setChosen((c) => { const n = { ...c }; groupOptions.forEach((o) => { if (n[o]) n[o] = { isDefault: o === optionId ? !n[o].isDefault : false } }); return n })

  return (
    <div className="max-w-4xl space-y-6">
      <Link to="/admin/products" className="text-sm font-semibold text-primary">← All products</Link>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div><h1 className="text-3xl font-bold">{d.summary.name}</h1><div className="mt-2"><StatusBadge status={d.summary.status} /></div></div>
        {writable && <div className="flex flex-wrap gap-2">
          {d.summary.status !== 'ACTIVE' && <Button loading={busy === 'st'} onClick={() => run('st', () => post(`/admin/products/${id}/status`, { status: 'ACTIVE' }), 'Published.')}>Publish</Button>}
          {d.summary.status === 'ACTIVE' && <Button variant="ghost" onClick={() => run('st', () => post(`/admin/products/${id}/status`, { status: 'DRAFT' }), 'Moved back to draft.')}>Unpublish</Button>}
          {d.summary.status !== 'ARCHIVED' && <Button variant="ghost" onClick={() => run('st', () => post(`/admin/products/${id}/status`, { status: 'ARCHIVED' }), 'Archived.')}>Archive</Button>}
          <Button variant="danger" onClick={() => setConfirmDelete(true)}>Delete</Button></div>}
      </div>

      <Card title="Details">
        <div className="grid gap-4 sm:grid-cols-2">
          <label className="text-sm font-semibold text-ink">Name<input className={`${input} mt-1 font-normal`} value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} /></label>
          <label className="text-sm font-semibold text-ink">Category<select className={`${input} mt-1 font-normal`} value={form.categoryId} onChange={(e) => setForm({ ...form, categoryId: e.target.value })}>{categoryOptions(cats.data ?? []).map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}</select></label>
          <label className="text-sm font-semibold text-ink">Base price (₹)<input type="number" step="0.01" min="0" className={`${input} mt-1 font-normal`} value={form.basePrice} onChange={(e) => setForm({ ...form, basePrice: e.target.value })} /></label>
          <div className="sm:col-span-2"><span className="text-sm font-semibold text-ink">3D preview model</span>
            <div className="mt-1 flex flex-wrap items-center gap-3">
              <select aria-label="3D preview model" className={`${input} w-auto font-normal`} value={form.modelUrl.startsWith('builtin:') ? form.modelUrl : form.modelUrl ? 'glb' : ''}
                onChange={(e) => setForm({ ...form, modelUrl: e.target.value === 'glb' ? (form.modelUrl.startsWith('builtin:') ? '' : form.modelUrl) : e.target.value })}>
                <option value="">None (photos and 2D layers only)</option><option value="builtin:shirt">Built-in placeholder: shirt</option><option value="builtin:trousers">Built-in placeholder: trousers</option><option value="glb">Uploaded .glb model</option></select>
              {(form.modelUrl === '' || !form.modelUrl.startsWith('builtin:')) && <ModelUploader value={form.modelUrl} onChange={(u) => setForm({ ...form, modelUrl: u })} />}
            </div>
            <p className="mt-1 text-xs">Placeholders are simple shapes for testing the options. Upload a real .glb (up to 20 MB) named as in the 3D model guide; option bindings then drive it.</p></div>
          <label className="text-sm font-semibold text-ink sm:col-span-2">Description<textarea rows={4} className={`${input} mt-1 font-normal`} value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} /></label>
          <label className="flex items-center gap-2 text-sm font-semibold text-ink"><input type="checkbox" className="accent-primary" checked={form.featured} onChange={(e) => setForm({ ...form, featured: e.target.checked })} />Featured collection</label>
          <label className="flex items-center gap-2 text-sm font-semibold text-ink"><input type="checkbox" className="accent-primary" checked={form.trending} onChange={(e) => setForm({ ...form, trending: e.target.checked })} />Show as trending</label>
        </div>
        {writable && <Button className="mt-4" loading={busy === 'det'} onClick={() => run('det', () => http.put(`/admin/products/${id}`, { categoryId: Number(form.categoryId), name: form.name, description: form.description || null, basePrice: Number(form.basePrice), featured: form.featured, trending: form.trending, modelUrl: form.modelUrl || null }), 'Details saved.')}>Save details</Button>}
      </Card>

      <Card title="Images" note="The first image, or the one marked main, is the product thumbnail.">
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">{d.images.map((im) => (
          <figure key={im.id} className="rounded-lg ring-1 ring-ink/10"><img src={im.url} alt={im.alt ?? ''} className="aspect-square w-full rounded-t-lg object-cover" />
            <figcaption className="flex items-center justify-between p-2 text-xs">{im.primary ? <strong className="text-primary">Main</strong> : writable ? <button className="font-semibold text-primary" onClick={() => run('img', () => post(`/admin/products/${id}/images/${im.id}/primary`), 'Main image changed.')}>Make main</button> : <span />}
              {writable && <button className="font-semibold text-ink/70 hover:text-primary" onClick={() => run('img', () => http.delete(`/admin/products/${id}/images/${im.id}`), 'Image removed.')}>Remove</button>}</figcaption></figure>))}</div>
        {writable && <div className="mt-4"><ImageUploader label="Add image" onChange={(url) => { if (url) void run('img', () => post(`/admin/products/${id}/images`, { url }), 'Image added.') }} /></div>}
      </Card>

      <Card title="Sizes and fabrics" note="Custom measurements are always available alongside standard sizes (Phase 5).">
        <div className="grid gap-6 sm:grid-cols-2">
          <fieldset><legend className="text-sm font-bold text-ink">Sizes</legend><div className="mt-2 space-y-1">{(sizes.data ?? []).map((s) => <label key={s.id} className="flex items-center gap-2 text-sm"><input type="checkbox" className="accent-primary" checked={sizeIds.includes(s.id)} onChange={() => toggle(sizeIds, setSizeIds, s.id)} />{s.code} · {s.name}{!s.active && ' (hidden)'}</label>)}</div></fieldset>
          <fieldset><legend className="text-sm font-bold text-ink">Fabrics</legend><div className="mt-2 space-y-1">{(fabrics.data ?? []).length === 0 && <span className="text-xs">No fabrics yet. Add them under Fabrics, colours & sizes.</span>}{(fabrics.data ?? []).map((f) => <label key={f.id} className="flex items-center gap-2 text-sm"><input type="checkbox" className="accent-primary" checked={fabricIds.includes(f.id)} onChange={() => toggle(fabricIds, setFabricIds, f.id)} />{f.name}{!f.active && ' (hidden)'}</label>)}</div></fieldset>
        </div>
        {writable && <Button className="mt-4" loading={busy === 'sf'} onClick={async () => { await run('sf', () => http.put(`/admin/products/${id}/sizes`, { ids: sizeIds }), 'Sizes saved.'); await run('sf', () => http.put(`/admin/products/${id}/fabrics`, { ids: fabricIds }), 'Fabrics saved.') }}>Save sizes and fabrics</Button>}
      </Card>

      <Card title="Customization" note="Tick the groups this product offers, then the options inside each. Mark at most one default per group.">
        {(groups.data ?? []).length === 0 ? <EmptyState title="No customization groups exist yet" hint="Create groups and options under Customization first." /> : (
          <div className="space-y-4">{(groups.data ?? []).map((g) => (
            <div key={g.id} className="rounded-lg p-3 ring-1 ring-ink/10">
              <div className="flex flex-wrap items-center justify-between gap-2"><label className="flex items-center gap-2 font-semibold text-ink"><input type="checkbox" className="accent-primary" checked={!!offered[g.id]} onChange={(e) => setOffered((o) => { const n = { ...o }; if (e.target.checked) n[g.id] = { required: true }; else delete n[g.id]; return n })} />{g.name}</label>
                {offered[g.id] && <label className="flex items-center gap-2 text-xs"><input type="checkbox" className="accent-primary" checked={offered[g.id].required} onChange={(e) => setOffered((o) => ({ ...o, [g.id]: { required: e.target.checked } }))} />Customer must choose</label>}</div>
              {offered[g.id] && <ul className="mt-2 space-y-1 pl-6">{g.options.length === 0 && <li className="text-xs">This group has no options yet.</li>}{g.options.map((op) => (
                <li key={op.id} className="flex flex-wrap items-center justify-between gap-2 text-sm"><label className="flex items-center gap-2"><input type="checkbox" className="accent-primary" checked={!!chosen[op.id]} onChange={(e) => setChosen((c) => { const n = { ...c }; if (e.target.checked) n[op.id] = { isDefault: false }; else delete n[op.id]; return n })} />{op.name} <span className="text-xs">+₹{Number(op.priceDelta).toFixed(2)}</span></label>
                  {chosen[op.id] && <label className="flex items-center gap-1 text-xs"><input type="checkbox" className="accent-primary" checked={chosen[op.id].isDefault} onChange={() => setDefault(g.options.map((x) => x.id), op.id)} />Default</label>}</li>))}</ul>}
            </div>))}</div>)}
        {writable && (groups.data ?? []).length > 0 && <Button className="mt-4" loading={busy === 'cust'} onClick={saveCustomization}>Save customization</Button>}
      </Card>

      <ConfirmDialog open={confirmDelete} danger title="Delete this product?" message="It disappears from the catalog. Archive it instead if you may want it back." confirmLabel="Delete product" onCancel={() => setConfirmDelete(false)}
        onConfirm={async () => { setConfirmDelete(false); if (await run('del', () => http.delete(`/admin/products/${id}`), 'Product deleted.')) navigate('/admin/products') }} />
    </div>
  )
}
