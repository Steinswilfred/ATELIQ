import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Button } from '../../components/ui/Button'
import { EmptyState } from '../../components/ui/EmptyState'
import { Pagination } from '../../components/ui/Pagination'
import { Skeleton } from '../../components/ui/Skeleton'
import { StatusBadge } from '../../components/ui/StatusBadge'
import { get } from '../../lib/api'
import { money } from '../../lib/format'
import type { PageResult } from '../../lib/types'
import { useAsync } from '../../lib/useAsync'
import type { TailorCat } from './AdminTailorCategories'
import { selectClass } from './AdminUsers'

export interface TailorRow { userId: number; profileId: number; fullName: string; email: string; phone?: string; accountStatus: string; categoryId: number; categoryName: string; stitchingPrice: number; experienceYears: number; ratingAvg?: number; ratingCount: number; availability: string; maxActiveOrders: number }
export const availabilityLabel = (a: string) => ({ AVAILABLE: 'Available', BUSY: 'Busy', ON_LEAVE: 'On leave' } as Record<string, string>)[a] ?? a

export default function AdminTailors() {
  const [page, setPage] = useState(0)
  const [q, setQ] = useState(''); const [query, setQuery] = useState('')
  const [categoryId, setCategoryId] = useState(''); const [availability, setAvailability] = useState(''); const [status, setStatus] = useState('')
  const cats = useAsync(() => get<TailorCat[]>('/admin/tailor-categories').catch(() => [] as TailorCat[]), [])
  const { data, loading, error } = useAsync(() => get<PageResult<TailorRow>>(`/admin/tailors?page=${page}&size=20&categoryId=${categoryId}&availability=${availability}&status=${status}&q=${encodeURIComponent(query)}`), [page, categoryId, availability, status, query])
  return (
    <div>
      <h1 className="text-3xl font-bold">Tailors</h1>
      <p className="mt-1 text-sm">Approved tailors. New applications are reviewed under <Link to="/admin/tailors/pending" className="font-semibold text-primary">Tailor approvals</Link>.</p>
      <form className="mt-4 flex flex-wrap gap-2" onSubmit={(e) => { e.preventDefault(); setQuery(q); setPage(0) }}>
        <input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Search name, email or phone" aria-label="Search tailors" className={`${selectClass} w-64`} />
        <select value={categoryId} onChange={(e) => { setCategoryId(e.target.value); setPage(0) }} className={selectClass} aria-label="Category"><option value="">All categories</option>{(cats.data ?? []).map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}</select>
        <select value={availability} onChange={(e) => { setAvailability(e.target.value); setPage(0) }} className={selectClass} aria-label="Availability"><option value="">Any availability</option><option value="AVAILABLE">Available</option><option value="BUSY">Busy</option><option value="ON_LEAVE">On leave</option></select>
        <select value={status} onChange={(e) => { setStatus(e.target.value); setPage(0) }} className={selectClass} aria-label="Account status"><option value="">Any account status</option><option>APPROVED</option><option>BLOCKED</option><option>SUSPENDED</option></select>
        <Button type="submit" variant="dark">Search</Button>
      </form>
      <div className="mt-4 overflow-x-auto rounded-xl bg-canvas ring-1 ring-ink/10">
        {error ? <EmptyState title="Could not load tailors" hint={error.message} /> : loading ? <div className="space-y-2 p-4"><Skeleton className="h-8" /><Skeleton className="h-8" /></div>
          : data!.items.length === 0 ? <EmptyState title="No tailors match" hint="Approve applications to add tailors." /> : (
          <table className="w-full text-left text-sm"><thead className="border-b border-ink/10 text-xs text-body/70"><tr><th className="p-3">Tailor</th><th>Category</th><th>Price</th><th>Availability</th><th>Experience</th><th>Rating</th><th>Account</th></tr></thead>
            <tbody>{data!.items.map((t) => (
              <tr key={t.userId} className="border-b border-ink/5 hover:bg-surface"><td className="p-3"><Link to={`/admin/tailor-profiles/${t.userId}`} className="font-semibold text-ink hover:text-primary">{t.fullName}</Link><div className="text-xs">{t.email}</div></td>
                <td>{t.categoryName}</td><td>{money(t.stitchingPrice)}</td><td>{availabilityLabel(t.availability)} <span className="text-xs">(max {t.maxActiveOrders})</span></td><td>{t.experienceYears} yrs</td>
                <td>{t.ratingCount > 0 ? `${t.ratingAvg} (${t.ratingCount})` : 'No ratings yet'}</td><td><StatusBadge status={t.accountStatus} /></td></tr>))}</tbody></table>)}
      </div>
      {data && <Pagination page={data.page} size={data.size} total={data.total} onPage={setPage} />}
    </div>
  )
}
