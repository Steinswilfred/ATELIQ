import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { Button } from '../../components/ui/Button'
import { EmptyState } from '../../components/ui/EmptyState'
import { PromptDialog } from '../../components/ui/PromptDialog'
import { Skeleton } from '../../components/ui/Skeleton'
import { StatusBadge } from '../../components/ui/StatusBadge'
import { ApiError, get, http, post } from '../../lib/api'
import { useAsync } from '../../lib/useAsync'
import { useAuth } from '../../store/authStore'
import { toast } from '../../store/toastStore'
import type { UserRow } from './AdminUsers'

interface Detail {
  user: UserRow; statusReason?: string; roles: string[]; latestApplicationId?: number
  logins: { success: boolean; failureReason?: string; ipAddress?: string; at: string }[]
  activity: { action: string; entityType?: string; entityId?: string; at: string }[]
}

export default function AdminUserDetail() {
  const { id } = useParams()
  const can = useAuth((s) => s.can)
  const me = useAuth((s) => s.user)
  const { data, loading, error, reload } = useAsync(() => get<Detail>(`/admin/users/${id}`), [id])
  const [dialog, setDialog] = useState<'BLOCK' | 'SUSPEND' | 'REACTIVATE' | null>(null)
  const [name, setName] = useState<string | null>(null)

  const act = async (action: string, v: Record<string, string>) => {
    try { await post(`/admin/users/${id}/status`, { action, reason: v.reason }); toast.success('Account updated.'); setDialog(null); reload() }
    catch (e) { toast.error((e as ApiError).message) }
  }
  const saveName = async () => {
    try { await http.put(`/admin/users/${id}`, { fullName: name }); toast.success('Name saved.'); setName(null); reload() }
    catch (e) { toast.error((e as ApiError).message) }
  }
  const setVerified = async (field: 'emailVerified' | 'phoneVerified', value: boolean) => {
    try { await http.put(`/admin/users/${id}`, { [field]: value }); toast.success('Verification updated.'); reload() }
    catch (e) { toast.error((e as ApiError).message) }
  }

  if (loading) return <div className="space-y-3"><Skeleton className="h-10 w-1/3" /><Skeleton className="h-48" /></div>
  if (error || !data) return <EmptyState title="Could not load this user" hint={error?.message} />
  const u = data.user
  const manage = can('USER_MANAGE') && me?.id !== u.id && u.userType !== 'SUPER_ADMIN'
  const restricted = ['BLOCKED', 'SUSPENDED', 'DEACTIVATED'].includes(u.accountStatus)

  return (
    <div className="space-y-6">
      <Link to="/admin/users" className="text-sm font-semibold text-primary">← All users</Link>
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          {name === null ? <h1 className="text-3xl font-bold">{u.fullName}</h1> : (
            <div className="flex gap-2"><input value={name} onChange={(e) => setName(e.target.value)} className="rounded-lg border border-ink/20 px-3 py-2 text-lg" aria-label="Full name" /><Button onClick={saveName}>Save</Button><Button variant="ghost" onClick={() => setName(null)}>Cancel</Button></div>)}
          <p className="mt-1 text-sm">{u.email} · {u.phone ?? 'no phone'} · {u.userType}</p>
          <div className="mt-2 flex items-center gap-2"><StatusBadge status={u.accountStatus} />{data.statusReason && <span className="text-xs">“{data.statusReason}”</span>}</div>
        </div>
        {manage && (
          <div className="flex flex-wrap gap-2">
            {name === null && <Button variant="ghost" onClick={() => setName(u.fullName)}>Edit name</Button>}
            {restricted ? <Button onClick={() => setDialog('REACTIVATE')}>Reactivate</Button> : <>
              <Button variant="ghost" onClick={() => setDialog('SUSPEND')}>Suspend</Button>
              <Button variant="danger" onClick={() => setDialog('BLOCK')}>Block</Button></>}
          </div>)}
      </div>

      <section className="grid gap-4 md:grid-cols-2">
        <div className="rounded-xl bg-canvas p-5 ring-1 ring-ink/10">
          <h2 className="text-lg font-bold">Verification</h2>
          <ul className="mt-3 space-y-2 text-sm">
            {(['emailVerified', 'phoneVerified'] as const).map((f) => (
              <li key={f} className="flex items-center justify-between"><span>{f === 'emailVerified' ? 'Email' : 'Phone'}: {u[f] ? 'verified' : 'not verified'}</span>
                {manage && <button className="text-xs font-semibold text-primary" onClick={() => setVerified(f, !u[f])}>{u[f] ? 'Mark unverified' : 'Mark verified'}</button>}</li>))}
          </ul>
          <h2 className="mt-5 text-lg font-bold">Roles</h2>
          <p className="mt-2 text-sm">{data.roles.length ? data.roles.join(', ') : 'None'}</p>
          {data.latestApplicationId && can('TAILOR_APPLICATION_REVIEW') && <Link className="mt-4 inline-block text-sm font-semibold text-primary" to={`/admin/tailors/${data.latestApplicationId}`}>View tailor application →</Link>}
        </div>
        <div className="rounded-xl bg-canvas p-5 ring-1 ring-ink/10">
          <h2 className="text-lg font-bold">Sign-in history</h2>
          {data.logins.length === 0 ? <p className="mt-3 text-sm">No sign-ins recorded.</p> : (
            <ul className="mt-3 max-h-60 divide-y divide-ink/10 overflow-auto text-sm">{data.logins.map((l, i) => (
              <li key={i} className="flex justify-between py-1.5"><span>{l.success ? 'Signed in' : `Failed (${l.failureReason?.toLowerCase().replace(/_/g, ' ')})`} · {l.ipAddress}</span><time className="text-body/70">{new Date(l.at).toLocaleString()}</time></li>))}</ul>)}
        </div>
      </section>

      <section className="rounded-xl bg-canvas p-5 ring-1 ring-ink/10">
        <h2 className="text-lg font-bold">Recent activity</h2>
        {data.activity.length === 0 ? <p className="mt-3 text-sm">No activity recorded.</p> : (
          <ul className="mt-3 divide-y divide-ink/10 text-sm">{data.activity.map((a, i) => <li key={i} className="flex justify-between py-1.5"><span>{a.action.replace(/_/g, ' ').toLowerCase()}</span><time className="text-body/70">{new Date(a.at).toLocaleString()}</time></li>)}</ul>)}
      </section>

      <PromptDialog open={dialog === 'BLOCK'} danger title="Block this account?" description="They are signed out immediately and cannot sign in until reactivated."
        fields={[{ name: 'reason', label: 'Reason', required: true, multiline: true }]} submitLabel="Block account" onSubmit={(v) => act('BLOCK', v)} onCancel={() => setDialog(null)} />
      <PromptDialog open={dialog === 'SUSPEND'} title="Suspend this account?" description="A temporary restriction. They are signed out immediately."
        fields={[{ name: 'reason', label: 'Reason', required: true, multiline: true }]} submitLabel="Suspend account" onSubmit={(v) => act('SUSPEND', v)} onCancel={() => setDialog(null)} />
      <PromptDialog open={dialog === 'REACTIVATE'} title="Reactivate this account?" description="The account returns to the state it should be in (unverified or unapproved accounts stay that way)."
        fields={[]} submitLabel="Reactivate" onSubmit={(v) => act('REACTIVATE', v)} onCancel={() => setDialog(null)} />
    </div>
  )
}
