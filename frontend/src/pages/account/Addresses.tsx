import { useState } from 'react'
import { AddressForm } from '../../components/shop/AddressForm'
import { Button } from '../../components/ui/Button'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'
import { EmptyState } from '../../components/ui/EmptyState'
import { Skeleton } from '../../components/ui/Skeleton'
import { ApiError, get, http } from '../../lib/api'
import type { AddressT } from '../../lib/orderTypes'
import { useAsync } from '../../lib/useAsync'
import { toast } from '../../store/toastStore'

export default function Addresses() {
  const { data, loading, error, reload } = useAsync(() => get<AddressT[]>('/me/addresses'), [])
  const [edit, setEdit] = useState<{ open: boolean; a?: AddressT }>({ open: false })
  const [del, setDel] = useState<AddressT | null>(null)
  const remove = async () => { try { await http.delete(`/me/addresses/${del!.id}`); toast.success('Address deleted.'); reload() } catch (e) { toast.error((e as ApiError).message) } setDel(null) }
  return (
    <div>
      <div className="flex flex-wrap items-center justify-between gap-3"><h1 className="text-3xl font-bold">Delivery addresses</h1>{!edit.open && <Button onClick={() => setEdit({ open: true })}>Add address</Button>}</div>
      {edit.open && <div className="mt-6 rounded-xl bg-canvas p-5 ring-1 ring-ink/10"><AddressForm address={edit.a} onCancel={() => setEdit({ open: false })} onSaved={() => { setEdit({ open: false }); reload() }} /></div>}
      <div className="mt-6">
        {error ? <EmptyState title="Could not load addresses" hint={error.message} /> : loading ? <Skeleton className="h-32" /> : data!.length === 0 && !edit.open ? <EmptyState title="No saved addresses" hint="Add one to speed up checkout." />
          : <ul className="grid gap-4 sm:grid-cols-2">{data!.map((a) => (
            <li key={a.id} className="rounded-xl bg-canvas p-5 ring-1 ring-ink/10"><div className="flex justify-between"><strong className="text-ink">{a.label || a.fullName}</strong>{a.isDefault && <span className="rounded-full bg-primary/15 px-2 py-0.5 text-xs font-semibold text-primary">Default</span>}</div>
              <p className="mt-2 text-sm">{a.fullName}<br />{a.line1}{a.line2 ? `, ${a.line2}` : ''}<br />{a.city}, {a.state} {a.postalCode}<br />{a.phone}</p>
              <div className="mt-3 flex gap-3 text-sm font-semibold"><button className="text-primary" onClick={() => setEdit({ open: true, a })}>Edit</button><button className="text-ink/70 hover:text-primary" onClick={() => setDel(a)}>Delete</button></div></li>))}</ul>}
      </div>
      <ConfirmDialog open={!!del} danger title="Delete this address?" message="Past orders keep their own delivery details." confirmLabel="Delete" onConfirm={remove} onCancel={() => setDel(null)} />
    </div>
  )
}
