import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Button } from '../../components/ui/Button'
import { EmptyState } from '../../components/ui/EmptyState'
import { Pagination } from '../../components/ui/Pagination'
import { Skeleton } from '../../components/ui/Skeleton'
import { StatusBadge } from '../../components/ui/StatusBadge'
import { get } from '../../lib/api'
import type { PageResult } from '../../lib/types'
import { useAsync } from '../../lib/useAsync'
import { selectClass } from './AdminUsers'

export interface AppRow { id: number; userId: number; fullName: string; email: string; phone?: string; experienceYears: number; preferredCategory?: string; status: string; emailVerified: boolean; phoneVerified: boolean; createdAt: string }
const TABS = [['PENDING_APPROVAL', 'Pending'], ['APPROVED', 'Approved'], ['REJECTED', 'Rejected'], ['', 'All']] as const

export default function TailorApplications() {
  const [status, setStatus] = useState('PENDING_APPROVAL')
  const [q, setQ] = useState('')
  const [query, setQuery] = useState('')
  const [page, setPage] = useState(0)
  const { data, loading, error } = useAsync(() => get<PageResult<AppRow>>(`/admin/tailor-applications?page=${page}&size=20&status=${status}&q=${encodeURIComponent(query)}`), [status, query, page])
  return (
    <div>
      <h1 className="text-3xl font-bold">Tailor approvals</h1>
      <div className="mt-4 flex flex-wrap items-center gap-2">
        {TABS.map(([v, label]) => <Button key={label} variant={status === v ? 'dark' : 'ghost'} onClick={() => { setStatus(v); setPage(0) }}>{label}</Button>)}
        <select className={selectClass} aria-label="Other status" value={['PENDING_APPROVAL', 'APPROVED', 'REJECTED', ''].includes(status) ? '' : status} onChange={(e) => { if (e.target.value) { setStatus(e.target.value); setPage(0) } }}>
          <option value="">Not yet verified…</option><option value="PENDING_EMAIL_VERIFICATION">Pending email</option><option value="PENDING_PHONE_VERIFICATION">Pending phone</option></select>
        <form onSubmit={(e) => { e.preventDefault(); setQuery(q); setPage(0) }} className="ml-auto"><input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Search applicants" aria-label="Search applicants" className={`${selectClass} w-56`} /></form>
      </div>
      <div className="mt-4 overflow-x-auto rounded-xl bg-canvas ring-1 ring-ink/10">
        {error ? <EmptyState title="Could not load applications" hint={error.message} /> : loading ? <div className="space-y-2 p-4">{Array.from({ length: 5 }, (_, i) => <Skeleton key={i} className="h-8" />)}</div>
          : data!.items.length === 0 ? <EmptyState title="No applications here" hint="New tailor applications appear once the applicant verifies email and phone." /> : (
          <table className="w-full text-left text-sm">
            <thead className="border-b border-ink/10 text-xs text-body/70"><tr><th className="p-3">ID</th><th>Applicant</th><th>Experience</th><th>Requested category</th><th>Verification</th><th>Status</th><th>Applied</th><th /></tr></thead>
            <tbody>{data!.items.map((a) => (
              <tr key={a.id} className="border-b border-ink/5 hover:bg-surface">
                <td className="p-3">#{a.id}</td><td><div className="font-semibold text-ink">{a.fullName}</div><div className="text-xs">{a.email} · {a.phone}</div></td>
                <td>{a.experienceYears} yrs</td><td>{a.preferredCategory || '—'}</td>
                <td className="text-xs">{a.emailVerified ? 'Email ✓' : 'Email ✗'} · {a.phoneVerified ? 'Phone ✓' : 'Phone ✗'}</td>
                <td><StatusBadge status={a.status} /></td><td>{new Date(a.createdAt).toLocaleDateString()}</td>
                <td><Link className="font-semibold text-primary" to={`/admin/tailors/${a.id}`}>Review</Link></td>
              </tr>))}</tbody>
          </table>)}
      </div>
      {data && <Pagination page={data.page} size={data.size} total={data.total} onPage={setPage} />}
    </div>
  )
}
