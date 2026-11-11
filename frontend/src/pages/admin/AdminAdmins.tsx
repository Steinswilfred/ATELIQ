import { useState } from 'react'
import { Link } from 'react-router-dom'
import { AnimatePresence, motion } from 'framer-motion'
import { Button } from '../../components/ui/Button'
import { EmptyState } from '../../components/ui/EmptyState'
import { Skeleton } from '../../components/ui/Skeleton'
import { StatusBadge } from '../../components/ui/StatusBadge'
import { ApiError, get, http, post } from '../../lib/api'
import type { PageResult } from '../../lib/types'
import { useAsync } from '../../lib/useAsync'
import { useAuth } from '../../store/authStore'
import { toast } from '../../store/toastStore'
import type { UserRow } from './AdminUsers'

interface Role { id: number; code: string; name: string }
const ASSIGNABLE = (r: Role) => !['SUPER_ADMIN', 'CUSTOMER', 'TAILOR'].includes(r.code)
const cls = 'w-full rounded-lg border border-ink/20 bg-canvas px-3 py-2 text-sm focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/30'

function AdminDialog({ mode, roles, current, onClose, onDone, userId }: { mode: 'create' | 'roles'; roles: Role[]; current: string[]; onClose: () => void; onDone: () => void; userId?: number }) {
  const [form, setForm] = useState({ fullName: '', email: '', phone: '' })
  const [picked, setPicked] = useState<Set<string>>(new Set(current))
  const [busy, setBusy] = useState(false)
  const toggle = (c: string) => { const n = new Set(picked); n.has(c) ? n.delete(c) : n.add(c); setPicked(n) }
  const submit = async () => {
    setBusy(true)
    try {
      if (mode === 'create') await post('/admin/admins', { ...form, phone: form.phone || null, roleCodes: [...picked] })
      else await http.put(`/admin/users/${userId}/roles`, { roleCodes: [...picked] })
      toast.success(mode === 'create' ? 'Admin created. They received an email to set their password.' : 'Roles updated.')
      onDone()
    } catch (e) { toast.error((e as ApiError).details ? Object.values((e as ApiError).details!)[0] : (e as ApiError).message) } finally { setBusy(false) }
  }
  const invalid = mode === 'create' && (!form.fullName.trim() || !form.email.trim())
  return (
    <motion.div className="fixed inset-0 z-40 grid place-items-center bg-ink/50 p-4" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} onClick={onClose} role="dialog" aria-modal="true">
      <div className="w-full max-w-md rounded-2xl bg-canvas p-6 shadow-xl" onClick={(e) => e.stopPropagation()}>
        <h2 className="text-xl font-bold">{mode === 'create' ? 'Create an admin' : 'Edit roles'}</h2>
        {mode === 'create' && <div className="mt-4 space-y-3">
          <input className={cls} placeholder="Full name" aria-label="Full name" value={form.fullName} onChange={(e) => setForm({ ...form, fullName: e.target.value })} />
          <input className={cls} placeholder="Email" type="email" aria-label="Email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} />
          <input className={cls} placeholder="Phone (optional, +91…)" aria-label="Phone" value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} />
          <p className="text-xs">They get an email link to set their own password.</p></div>}
        <fieldset className="mt-4"><legend className="text-sm font-semibold text-ink">Roles</legend>
          {roles.filter(ASSIGNABLE).length === 0 ? <p className="mt-2 text-sm">No admin roles exist yet. Create one under Roles & permissions first.</p> :
            <div className="mt-2 space-y-1">{roles.filter(ASSIGNABLE).map((r) => <label key={r.code} className="flex items-center gap-2 text-sm"><input type="checkbox" className="accent-primary" checked={picked.has(r.code)} onChange={() => toggle(r.code)} />{r.name}</label>)}</div>}
        </fieldset>
        <div className="mt-6 flex justify-end gap-2"><Button variant="ghost" onClick={onClose}>Cancel</Button><Button loading={busy} disabled={invalid} onClick={submit}>{mode === 'create' ? 'Create admin' : 'Save roles'}</Button></div>
      </div>
    </motion.div>
  )
}

export default function AdminAdmins() {
  const can = useAuth((s) => s.can)
  const admins = useAsync(() => get<PageResult<UserRow>>('/admin/users?type=ADMIN&size=100'), [])
  const roles = useAsync(() => get<Role[]>('/admin/roles'), [])
  const [dialog, setDialog] = useState<{ mode: 'create' | 'roles'; userId?: number; current: string[] } | null>(null)
  const openRoles = async (id: number) => { const d = await get<{ roles: string[] }>(`/admin/users/${id}`); setDialog({ mode: 'roles', userId: id, current: d.roles }) }

  return (
    <div>
      <div className="flex flex-wrap items-center justify-between gap-3"><h1 className="text-3xl font-bold">Admins</h1>
        {can('ADMIN_CREATE') && <Button onClick={() => setDialog({ mode: 'create', current: [] })}>Create admin</Button>}</div>
      <div className="mt-6 overflow-x-auto rounded-xl bg-canvas ring-1 ring-ink/10">
        {admins.error ? <EmptyState title="Could not load admins" hint={admins.error.message} /> : admins.loading ? <div className="space-y-2 p-4"><Skeleton className="h-8" /><Skeleton className="h-8" /></div>
          : admins.data!.items.length === 0 ? <EmptyState title="No admins yet" hint="Create an admin and give them only the roles they need." /> : (
          <table className="w-full text-left text-sm"><thead className="border-b border-ink/10 text-xs text-body/70"><tr><th className="p-3">Admin</th><th>Status</th><th>Last sign-in</th><th /></tr></thead>
            <tbody>{admins.data!.items.map((a) => (
              <tr key={a.id} className="border-b border-ink/5"><td className="p-3"><Link className="font-semibold text-ink hover:text-primary" to={`/admin/users/${a.id}`}>{a.fullName}</Link><div className="text-xs">{a.email}</div></td>
                <td><StatusBadge status={a.accountStatus} /></td><td>{a.lastLoginAt ? new Date(a.lastLoginAt).toLocaleString() : 'Never'}</td>
                <td className="text-right">{can('ADMIN_MANAGE') && <button className="pr-3 font-semibold text-primary" onClick={() => openRoles(a.id)}>Edit roles</button>}</td></tr>))}</tbody></table>)}
      </div>
      <AnimatePresence>{dialog && roles.data && <AdminDialog {...dialog} roles={roles.data} onClose={() => setDialog(null)} onDone={() => { setDialog(null); admins.reload() }} />}</AnimatePresence>
    </div>
  )
}
