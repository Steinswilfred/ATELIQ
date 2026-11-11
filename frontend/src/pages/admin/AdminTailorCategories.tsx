import { useState } from 'react'
import { Button } from '../../components/ui/Button'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'
import { EmptyState } from '../../components/ui/EmptyState'
import { Field, FormDialog, Values } from '../../components/ui/FormDialog'
import { Skeleton } from '../../components/ui/Skeleton'
import { ApiError, get, http, post } from '../../lib/api'
import { money } from '../../lib/format'
import { useAsync } from '../../lib/useAsync'
import { toast } from '../../store/toastStore'

export interface TailorCat { id: number; name: string; description?: string; level: number; stitchingPrice: number; active: boolean; tailorCount: number }
const fields: Field[] = [
  { name: 'name', label: 'Name', required: true, hint: 'e.g. Beginner, Professional, Master Tailor' },
  { name: 'level', label: 'Skill level (1 = lowest)', type: 'number', required: true, hint: 'Sets the order shown to customers.' },
  { name: 'stitchingPrice', label: 'Stitching price per garment (₹)', type: 'number', step: '0.01', required: true, hint: 'Added to the customer’s price. Changing it affects new quotes only.' },
  { name: 'description', label: 'Description', type: 'textarea' },
  { name: 'active', label: 'Offered to customers and available for new tailors', type: 'checkbox' },
]

export default function AdminTailorCategories() {
  const { data, loading, error, reload } = useAsync(() => get<TailorCat[]>('/admin/tailor-categories'), [])
  const [edit, setEdit] = useState<{ open: boolean; c?: TailorCat }>({ open: false })
  const [del, setDel] = useState<TailorCat | null>(null)
  const c = edit.c
  const initial: Values = c ? { name: c.name, level: String(c.level), stitchingPrice: String(c.stitchingPrice), description: c.description ?? '', active: c.active }
    : { name: '', level: String((data?.length ?? 0) + 1), stitchingPrice: '', description: '', active: true }
  const save = async (v: Values) => {
    const body = { name: v.name, level: Number(v.level), stitchingPrice: Number(v.stitchingPrice), description: v.description || null, active: v.active }
    if (c) await http.put(`/admin/tailor-categories/${c.id}`, body); else await post('/admin/tailor-categories', body)
    toast.success('Category saved.'); setEdit({ open: false }); reload()
  }
  const remove = async () => { try { await http.delete(`/admin/tailor-categories/${del!.id}`); toast.success('Category deleted.'); reload() } catch (e) { toast.error((e as ApiError).message) } setDel(null) }

  if (error) return <EmptyState title="Could not load tailor categories" hint={error.message} />
  return (
    <div>
      <div className="flex flex-wrap items-center justify-between gap-3"><h1 className="text-3xl font-bold">Tailor categories</h1><Button onClick={() => setEdit({ open: true })}>Add category</Button></div>
      <p className="mt-1 text-sm">Skill levels and the stitching price for each. Customers pick a level; tailors in that level see the order. The starting five were created with example prices, so set yours.</p>
      <div className="mt-6 space-y-3">
        {loading ? <Skeleton className="h-40" /> : data!.length === 0 ? <EmptyState title="No tailor categories" hint="Add at least one so tailors can be approved." /> : data!.map((t) => (
          <section key={t.id} className="flex flex-wrap items-center justify-between gap-3 rounded-xl bg-canvas p-5 ring-1 ring-ink/10">
            <div><h2 className="text-lg font-bold">{t.name}{!t.active && <span className="ml-2 text-xs font-normal">(hidden)</span>}</h2>
              <p className="text-sm font-semibold text-ink">{money(t.stitchingPrice)} <span className="font-normal text-xs">per garment · level {t.level} · {t.tailorCount} tailor{t.tailorCount === 1 ? '' : 's'}</span></p>
              {t.description && <p className="mt-1 max-w-xl text-xs">{t.description}</p>}</div>
            <div className="flex gap-3 text-sm font-semibold"><button className="text-primary" onClick={() => setEdit({ open: true, c: t })}>Edit</button><button className="text-ink/70 hover:text-primary" onClick={() => setDel(t)}>Delete</button></div>
          </section>))}
      </div>
      <FormDialog open={edit.open} title={c ? 'Edit tailor category' : 'New tailor category'} fields={fields} initial={initial} onSubmit={save} onCancel={() => setEdit({ open: false })} />
      <ConfirmDialog open={!!del} danger title={`Delete “${del?.name}”?`} message="Categories with tailors assigned cannot be deleted; hide them instead." confirmLabel="Delete category" onConfirm={remove} onCancel={() => setDel(null)} />
    </div>
  )
}
