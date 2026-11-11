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

interface Coupon { id: number; code: string; description?: string; discountType: 'PERCENT' | 'FIXED'; value: number; minOrderAmount: number; maxDiscount?: number; startsAt?: string; endsAt?: string; usageLimit?: number; perUserLimit?: number; usedCount: number; active: boolean }
const fields: Field[] = [
  { name: 'code', label: 'Code', required: true, hint: 'Customers type this. Letters, digits, - or _.' }, { name: 'description', label: 'Description (internal)' },
  { name: 'discountType', label: 'Type', type: 'select', options: [{ value: 'PERCENT', label: 'Percentage off the items' }, { value: 'FIXED', label: 'Fixed amount off the items' }] },
  { name: 'value', label: 'Value (% or ₹)', type: 'number', step: '0.01', required: true }, { name: 'minOrderAmount', label: 'Minimum items total (₹)', type: 'number', step: '0.01' },
  { name: 'maxDiscount', label: 'Maximum discount (₹, percentage coupons)', type: 'number', step: '0.01' },
  { name: 'startsAt', label: 'Starts (optional)', hint: 'YYYY-MM-DD HH:MM, in your local time' }, { name: 'endsAt', label: 'Ends (optional)', hint: 'YYYY-MM-DD HH:MM' },
  { name: 'usageLimit', label: 'Total uses allowed (optional)', type: 'number' }, { name: 'perUserLimit', label: 'Uses per customer (optional)', type: 'number' }, { name: 'active', label: 'Active', type: 'checkbox' },
]
const toIso = (s?: string | boolean | string[]) => { const t = String(s ?? '').trim(); if (!t) return null; const d = new Date(t.replace(' ', 'T')); if (Number.isNaN(d.getTime())) throw new ApiError('INVALID_DATE', 'Dates must look like 2026-12-31 18:00.'); return d.toISOString() }
const local = (iso?: string) => { if (!iso) return ''; const d = new Date(iso); const p = (n: number) => String(n).padStart(2, '0'); return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}` }

export default function AdminCoupons() {
  const { data, loading, error, reload } = useAsync(() => get<Coupon[]>('/admin/coupons'), [])
  const [edit, setEdit] = useState<{ open: boolean; c?: Coupon }>({ open: false })
  const [del, setDel] = useState<Coupon | null>(null)
  const c = edit.c
  const initial: Values = c ? { code: c.code, description: c.description ?? '', discountType: c.discountType, value: String(c.value), minOrderAmount: String(c.minOrderAmount), maxDiscount: c.maxDiscount ? String(c.maxDiscount) : '', startsAt: local(c.startsAt), endsAt: local(c.endsAt), usageLimit: c.usageLimit ? String(c.usageLimit) : '', perUserLimit: c.perUserLimit ? String(c.perUserLimit) : '', active: c.active }
    : { code: '', description: '', discountType: 'PERCENT', value: '', minOrderAmount: '0', maxDiscount: '', startsAt: '', endsAt: '', usageLimit: '', perUserLimit: '', active: true }
  const num = (v: string | boolean | string[]) => (String(v ?? '').trim() === '' ? null : Number(v))
  const save = async (v: Values) => {
    const body = { code: v.code, description: v.description || null, discountType: v.discountType, value: Number(v.value), minOrderAmount: num(v.minOrderAmount) ?? 0, maxDiscount: num(v.maxDiscount), startsAt: toIso(v.startsAt), endsAt: toIso(v.endsAt), usageLimit: num(v.usageLimit), perUserLimit: num(v.perUserLimit), active: v.active }
    if (c) await http.put(`/admin/coupons/${c.id}`, body); else await post('/admin/coupons', body)
    toast.success('Coupon saved.'); setEdit({ open: false }); reload()
  }
  const remove = async () => { try { await http.delete(`/admin/coupons/${del!.id}`); toast.success('Coupon deleted.'); reload() } catch (e) { toast.error((e as ApiError).message) } setDel(null) }
  if (error) return <EmptyState title="Could not load coupons" hint={error.message} />
  return (
    <div>
      <div className="flex flex-wrap items-center justify-between gap-3"><h1 className="text-3xl font-bold">Coupons</h1><Button onClick={() => setEdit({ open: true })}>New coupon</Button></div>
      <p className="mt-1 text-sm">Discounts apply to the items total, not to stitching, tax or delivery.</p>
      <div className="mt-6 space-y-3">{loading ? <Skeleton className="h-32" /> : data!.length === 0 ? <EmptyState title="No coupons yet" hint="Create one for customers to enter at checkout." /> : data!.map((k) => (
        <section key={k.id} className="flex flex-wrap items-center justify-between gap-3 rounded-xl bg-canvas p-5 ring-1 ring-ink/10">
          <div><h2 className="font-bold text-ink">{k.code}{!k.active && <span className="ml-2 text-xs font-normal">(inactive)</span>}</h2>
            <p className="text-sm">{k.discountType === 'PERCENT' ? `${k.value}% off` : `${money(k.value)} off`}{k.maxDiscount ? `, up to ${money(k.maxDiscount)}` : ''}{k.minOrderAmount > 0 ? ` · min ${money(k.minOrderAmount)}` : ''}</p>
            <p className="text-xs">Used {k.usedCount}{k.usageLimit ? ` of ${k.usageLimit}` : ''} times{k.perUserLimit ? ` · ${k.perUserLimit} per customer` : ''}{k.endsAt ? ` · ends ${new Date(k.endsAt).toLocaleDateString()}` : ''}</p></div>
          <div className="flex gap-3 text-sm font-semibold"><button className="text-primary" onClick={() => setEdit({ open: true, c: k })}>Edit</button><button className="text-ink/70 hover:text-primary" onClick={() => setDel(k)}>Delete</button></div></section>))}</div>
      <FormDialog open={edit.open} title={c ? 'Edit coupon' : 'New coupon'} fields={fields} initial={initial} onSubmit={save} onCancel={() => setEdit({ open: false })} />
      <ConfirmDialog open={!!del} danger title={`Delete ${del?.code}?`} message="Customers will no longer be able to use it." confirmLabel="Delete" onConfirm={remove} onCancel={() => setDel(null)} />
    </div>
  )
}
