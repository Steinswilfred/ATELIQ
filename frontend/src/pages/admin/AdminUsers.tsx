import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { Button } from '../../components/ui/Button'
import { EmptyState } from '../../components/ui/EmptyState'
import { Pagination } from '../../components/ui/Pagination'
import { Skeleton } from '../../components/ui/Skeleton'
import { StatusBadge } from '../../components/ui/StatusBadge'
import { get } from '../../lib/api'
import type { PageResult } from '../../lib/types'
import { useAsync } from '../../lib/useAsync'
import { useAuth } from '../../store/authStore'

export interface UserRow { id: number; fullName: string; email: string; phone?: string; userType: string; accountStatus: string; emailVerified: boolean; phoneVerified: boolean; createdAt: string; lastLoginAt?: string }
const STATUSES = ['ACTIVE', 'APPROVED', 'PENDING_APPROVAL', 'PENDING_EMAIL_VERIFICATION', 'PENDING_PHONE_VERIFICATION', 'REJECTED', 'BLOCKED', 'SUSPENDED']
export const selectClass = 'rounded-lg border border-ink/20 bg-canvas px-3 py-2 text-sm'

export default function AdminUsers() {
  const [params, setParams] = useSearchParams()
  const can = useAuth((s) => s.can)
  const [page, setPage] = useState(0)
  const [q, setQ] = useState(params.get('q') ?? '')
  const type = params.get('type') ?? ''
  const status = params.get('status') ?? ''
  const query = params.get('q') ?? ''
  const { data, loading, error } = useAsync(
    () => get<PageResult<UserRow>>(`/admin/users?page=${page}&size=20&type=${type}&status=${status}&q=${encodeURIComponent(query)}`), [page, type, status, query])
  const set = (k: string, v: string) => { const n = new URLSearchParams(params); v ? n.set(k, v) : n.delete(k); setParams(n); setPage(0) }
  const types = ['CUSTOMER', 'TAILOR', ...(can('ADMIN_MANAGE') ? ['ADMIN', 'SUPER_ADMIN'] : [])]

  return (
    <div>
      <h1 className="text-3xl font-bold">Users</h1>
      <form className="mt-4 flex flex-wrap gap-2" onSubmit={(e) => { e.preventDefault(); set('q', q) }}>
        <input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Search name, email or phone" className={`${selectClass} w-64`} aria-label="Search users" />
        <select value={type} onChange={(e) => set('type', e.target.value)} className={selectClass} aria-label="Type"><option value="">All types</option>{types.map((t) => <option key={t}>{t}</option>)}</select>
        <select value={status} onChange={(e) => set('status', e.target.value)} className={selectClass} aria-label="Status"><option value="">All statuses</option>{STATUSES.map((s) => <option key={s}>{s}</option>)}</select>
        <Button type="submit" variant="dark">Search</Button>
      </form>
      <div className="mt-4 overflow-x-auto rounded-xl bg-canvas ring-1 ring-ink/10">
        {error ? <EmptyState title="Could not load users" hint={error.message} /> : loading ? <div className="space-y-2 p-4">{Array.from({ length: 6 }, (_, i) => <Skeleton key={i} className="h-8" />)}</div>
          : data!.items.length === 0 ? <EmptyState title="No users match" hint="Try clearing the filters." /> : (
          <table className="w-full text-left text-sm">
            <thead className="border-b border-ink/10 text-xs text-body/70"><tr><th className="p-3">Name</th><th>Type</th><th>Status</th><th>Verified</th><th>Joined</th><th>Last sign-in</th></tr></thead>
            <tbody>{data!.items.map((u) => (
              <tr key={u.id} className="border-b border-ink/5 hover:bg-surface">
                <td className="p-3"><Link to={`/admin/users/${u.id}`} className="font-semibold text-ink hover:text-primary">{u.fullName}</Link><div className="text-xs">{u.email}</div></td>
                <td>{u.userType}</td><td><StatusBadge status={u.accountStatus} /></td>
                <td className="text-xs">{u.emailVerified ? 'Email ✓' : 'Email ✗'} · {u.phoneVerified ? 'Phone ✓' : 'Phone ✗'}</td>
                <td>{new Date(u.createdAt).toLocaleDateString()}</td><td>{u.lastLoginAt ? new Date(u.lastLoginAt).toLocaleString() : '—'}</td>
              </tr>))}</tbody>
          </table>)}
      </div>
      {data && <Pagination page={data.page} size={data.size} total={data.total} onPage={setPage} />}
    </div>
  )
}
