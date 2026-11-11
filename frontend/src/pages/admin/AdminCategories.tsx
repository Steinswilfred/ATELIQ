import { useState } from 'react'
import { Button } from '../../components/ui/Button'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'
import { EmptyState } from '../../components/ui/EmptyState'
import { Field, FormDialog, Values } from '../../components/ui/FormDialog'
import { Skeleton } from '../../components/ui/Skeleton'
import { ApiError, get, http, post } from '../../lib/api'
import { useAsync } from '../../lib/useAsync'
import { toast } from '../../store/toastStore'

export interface Category { id: number; parentId: number | null; name: string; slug: string; description?: string; imageUrl?: string; sortOrder: number; active: boolean; productCount: number }

export default function AdminCategories() {
  const { data, loading, error, reload } = useAsync(() => get<Category[]>('/admin/categories'), [])
  const [edit, setEdit] = useState<{ open: boolean; cat?: Category; parentId?: number }>({ open: false })
  const [del, setDel] = useState<Category | null>(null)
  const roots = data?.filter((c) => c.parentId === null) ?? []

  const fields: Field[] = [
    { name: 'name', label: 'Name', required: true },
    { name: 'parentId', label: 'Belongs to', type: 'select', options: [{ value: '', label: 'Top level (section)' }, ...roots.filter((r) => r.id !== edit.cat?.id).map((r) => ({ value: String(r.id), label: r.name }))] },
    { name: 'description', label: 'Description', type: 'textarea' },
    { name: 'imageUrl', label: 'Image', type: 'image' },
    { name: 'sortOrder', label: 'Sort order', type: 'number' },
    { name: 'active', label: 'Visible to customers', type: 'checkbox' },
  ]
  const initial: Values = edit.cat ? { name: edit.cat.name, parentId: edit.cat.parentId ? String(edit.cat.parentId) : '', description: edit.cat.description ?? '', imageUrl: edit.cat.imageUrl ?? '', sortOrder: String(edit.cat.sortOrder), active: edit.cat.active }
    : { name: '', parentId: edit.parentId ? String(edit.parentId) : '', description: '', imageUrl: '', sortOrder: '0', active: true }

  const save = async (v: Values) => {
    const body = { name: v.name, parentId: v.parentId ? Number(v.parentId) : null, description: v.description || null, imageUrl: v.imageUrl || null, sortOrder: Number(v.sortOrder || 0), active: v.active }
    if (edit.cat) await http.put(`/admin/categories/${edit.cat.id}`, body); else await post('/admin/categories', body)
    toast.success('Category saved.'); setEdit({ open: false }); reload()
  }
  const remove = async () => {
    try { await http.delete(`/admin/categories/${del!.id}`); toast.success('Category deleted.'); reload() } catch (e) { toast.error((e as ApiError).message) }
    setDel(null)
  }

  if (error) return <EmptyState title="Could not load categories" hint={error.message} />
  return (
    <div>
      <div className="flex flex-wrap items-center justify-between gap-3"><h1 className="text-3xl font-bold">Categories</h1><Button onClick={() => setEdit({ open: true })}>Add category</Button></div>
      <p className="mt-1 text-sm">Sections (Men, Women, Kids) contain garment types such as Shirts or Trousers. Products live in a garment type.</p>
      <div className="mt-6 space-y-4">
        {loading ? <Skeleton className="h-40" /> : roots.map((r) => (
          <section key={r.id} className="rounded-xl bg-canvas p-5 ring-1 ring-ink/10">
            <Row c={r} onEdit={() => setEdit({ open: true, cat: r })} onDelete={() => setDel(r)} root />
            <ul className="mt-3 divide-y divide-ink/10 border-t border-ink/10">
              {data!.filter((c) => c.parentId === r.id).map((c) => <li key={c.id} className="py-2 pl-4"><Row c={c} onEdit={() => setEdit({ open: true, cat: c })} onDelete={() => setDel(c)} /></li>)}
            </ul>
            <button className="mt-3 text-sm font-semibold text-primary" onClick={() => setEdit({ open: true, parentId: r.id })}>+ Add garment type to {r.name}</button>
          </section>))}
      </div>
      <FormDialog open={edit.open} title={edit.cat ? 'Edit category' : 'New category'} fields={fields} initial={initial} onSubmit={save} onCancel={() => setEdit({ open: false })} />
      <ConfirmDialog open={!!del} danger title={`Delete “${del?.name}”?`} message="Categories with sub-categories or products cannot be deleted." confirmLabel="Delete" onConfirm={remove} onCancel={() => setDel(null)} />
    </div>
  )
}

function Row({ c, onEdit, onDelete, root }: { c: Category; onEdit: () => void; onDelete: () => void; root?: boolean }) {
  return (
    <div className="flex flex-wrap items-center justify-between gap-2">
      <div className="flex items-center gap-3">{c.imageUrl && <img src={c.imageUrl} alt="" className="h-9 w-9 rounded object-cover" />}
        <div><span className={root ? 'text-lg font-bold text-ink' : 'font-semibold text-ink'}>{c.name}</span>{!c.active && <span className="ml-2 text-xs">(hidden)</span>}<div className="text-xs">{c.productCount} product{c.productCount === 1 ? '' : 's'}</div></div></div>
      <div className="flex gap-3 text-sm font-semibold"><button className="text-primary" onClick={onEdit}>Edit</button><button className="text-ink/70 hover:text-primary" onClick={onDelete}>Delete</button></div>
    </div>
  )
}
