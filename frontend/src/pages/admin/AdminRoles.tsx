import { useEffect, useMemo, useState } from 'react'
import { Button } from '../../components/ui/Button'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'
import { EmptyState } from '../../components/ui/EmptyState'
import { PromptDialog } from '../../components/ui/PromptDialog'
import { Skeleton } from '../../components/ui/Skeleton'
import { ApiError, get, http, post } from '../../lib/api'
import { useAsync } from '../../lib/useAsync'
import { useAuth } from '../../store/authStore'
import { toast } from '../../store/toastStore'

interface Role { id: number; code: string; name: string; description?: string; system: boolean; permissions: string[] }
interface Perm { id: number; code: string; module: string; description?: string }

export default function AdminRoles() {
  const roles = useAsync(() => get<Role[]>('/admin/roles'), [])
  const perms = useAsync(() => get<Perm[]>('/admin/permissions'), [])
  const me = useAuth((s) => s.user)
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [checked, setChecked] = useState<Set<string>>(new Set())
  const [dialog, setDialog] = useState<'role' | 'perm' | 'delete' | null>(null)
  const [saving, setSaving] = useState(false)

  const selected = roles.data?.find((r) => r.id === selectedId) ?? null
  useEffect(() => { if (selected) setChecked(new Set(selected.permissions)) }, [selected?.id, roles.data]) // eslint-disable-line react-hooks/exhaustive-deps
  const grouped = useMemo(() => {
    const m = new Map<string, Perm[]>()
    perms.data?.forEach((p) => m.set(p.module, [...(m.get(p.module) ?? []), p]))
    return [...m.entries()]
  }, [perms.data])

  const locked = !selected || selected.code === 'SUPER_ADMIN' || (selected.system && me?.userType !== 'SUPER_ADMIN')
  const toggle = (code: string) => { const n = new Set(checked); n.has(code) ? n.delete(code) : n.add(code); setChecked(n) }
  const run = async (fn: () => Promise<unknown>, ok: string) => { try { await fn(); toast.success(ok); roles.reload(); perms.reload(); return true } catch (e) { toast.error((e as ApiError).message); return false } }

  if (roles.error) return <EmptyState title="Could not load roles" hint={roles.error.message} />
  return (
    <div>
      <div className="flex flex-wrap items-center justify-between gap-3"><h1 className="text-3xl font-bold">Roles & permissions</h1>
        <div className="flex gap-2"><Button variant="ghost" onClick={() => setDialog('perm')}>New permission</Button><Button onClick={() => setDialog('role')}>New role</Button></div></div>
      <div className="mt-6 grid gap-6 lg:grid-cols-[18rem_1fr]">
        <ul className="space-y-2">
          {roles.loading ? Array.from({ length: 4 }, (_, i) => <Skeleton key={i} className="h-14" />) : roles.data!.map((r) => (
            <li key={r.id}><button onClick={() => setSelectedId(r.id)} className={`w-full rounded-xl p-3 text-left ring-1 transition ${r.id === selectedId ? 'bg-ink text-white ring-ink' : 'bg-canvas ring-ink/10 hover:ring-primary'}`}>
              <div className="font-semibold">{r.name}</div><div className="text-xs opacity-70">{r.code}{r.system ? ' · system' : ''} · {r.permissions.length} permissions</div></button></li>))}
        </ul>
        <div className="rounded-xl bg-canvas p-5 ring-1 ring-ink/10">
          {!selected ? <EmptyState title="Choose a role" hint="Select a role on the left to see and change its permissions." /> : (<>
            <div className="flex flex-wrap items-center justify-between gap-2"><div><h2 className="text-xl font-bold">{selected.name}</h2><p className="text-sm">{selected.description}</p></div>
              {!selected.system && <Button variant="danger" onClick={() => setDialog('delete')}>Delete role</Button>}</div>
            {selected.code === 'SUPER_ADMIN' && <p className="mt-3 rounded-lg bg-surface p-3 text-sm">The Super Admin role always holds every permission and cannot be changed.</p>}
            {selected.system && selected.code !== 'SUPER_ADMIN' && me?.userType !== 'SUPER_ADMIN' && <p className="mt-3 rounded-lg bg-surface p-3 text-sm">Only the Super Admin can change system roles.</p>}
            <div className="mt-4 space-y-5">{grouped.map(([mod, list]) => (
              <fieldset key={mod}><legend className="text-xs font-bold text-body/70">{mod}</legend>
                <div className="mt-2 grid gap-2 sm:grid-cols-2">{list.map((p) => (
                  <label key={p.code} className="flex items-start gap-2 text-sm"><input type="checkbox" className="mt-1 accent-primary" disabled={locked || (me?.userType !== 'SUPER_ADMIN' && !me?.permissions.includes(p.code))}
                    checked={selected.code === 'SUPER_ADMIN' || checked.has(p.code)} onChange={() => toggle(p.code)} /><span><span className="font-semibold text-ink">{p.code}</span><br /><span className="text-xs">{p.description}</span></span></label>))}</div></fieldset>))}</div>
            {!locked && <Button className="mt-6" loading={saving} onClick={async () => { setSaving(true); await run(() => http.put(`/admin/roles/${selected.id}/permissions`, { permissionCodes: [...checked] }), 'Permissions saved.'); setSaving(false) }}>Save permissions</Button>}
          </>)}
        </div>
      </div>

      <PromptDialog open={dialog === 'role'} title="New role" description="Create a role, then choose its permissions."
        fields={[{ name: 'code', label: 'Code', required: true, placeholder: 'ORDER_MANAGER' }, { name: 'name', label: 'Name', required: true }, { name: 'description', label: 'Description', multiline: true }]}
        submitLabel="Create role" onCancel={() => setDialog(null)} onSubmit={async (v) => { if (await run(() => post('/admin/roles', v), 'Role created.')) setDialog(null) }} />
      <PromptDialog open={dialog === 'perm'} title="New permission" description="Permissions are checked by the backend, so a new one only has an effect once code enforces it."
        fields={[{ name: 'code', label: 'Code', required: true, placeholder: 'COUPON_MANAGE' }, { name: 'module', label: 'Module', required: true }, { name: 'description', label: 'Description', multiline: true }]}
        submitLabel="Create permission" onCancel={() => setDialog(null)} onSubmit={async (v) => { if (await run(() => post('/admin/permissions', v), 'Permission created.')) setDialog(null) }} />
      <ConfirmDialog open={dialog === 'delete'} danger title="Delete this role?" message="Roles still assigned to users cannot be deleted." confirmLabel="Delete role" onCancel={() => setDialog(null)}
        onConfirm={async () => { if (selected && await run(() => http.delete(`/admin/roles/${selected.id}`), 'Role deleted.')) { setSelectedId(null); setDialog(null) } else setDialog(null) }} />
    </div>
  )
}
