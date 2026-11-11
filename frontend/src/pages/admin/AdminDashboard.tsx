import { Link } from 'react-router-dom'
import { Skeleton } from '../../components/ui/Skeleton'
import { EmptyState } from '../../components/ui/EmptyState'
import { get } from '../../lib/api'
import { useAsync } from '../../lib/useAsync'

interface Stats { customers: number; tailors: number; activeTailors: number; pendingTailorApplications: number; blockedAccounts: number; recent: { action: string; actor?: string; at: string }[] }

export default function AdminDashboard() {
  const { data, loading, error } = useAsync(() => get<Stats>('/admin/dashboard'), [])
  const tiles = data ? [
    ['Customers', data.customers, '/admin/users?type=CUSTOMER'],
    ['Tailors', data.tailors, '/admin/users?type=TAILOR'],
    ['Active tailors', data.activeTailors, '/admin/users?type=TAILOR&status=APPROVED'],
    ['Awaiting approval', data.pendingTailorApplications, '/admin/tailors/pending'],
    ['Blocked or suspended', data.blockedAccounts, '/admin/users?status=BLOCKED'],
  ] as const : []
  return (
    <div>
      <h1 className="text-3xl font-bold">Dashboard</h1>
      <p className="mt-1 text-sm">Order, product and revenue figures appear here as those modules are built.</p>
      {error && <div className="mt-6"><EmptyState title="Could not load the dashboard" hint={error.message} /></div>}
      <div className="mt-6 grid gap-4 sm:grid-cols-2 xl:grid-cols-5">
        {loading ? Array.from({ length: 5 }, (_, i) => <Skeleton key={i} className="h-24" />) : tiles.map(([label, n, to]) => (
          <Link key={label} to={to} className="rounded-xl bg-canvas p-5 shadow-sm ring-1 ring-ink/10 transition hover:ring-primary">
            <div className="text-3xl font-bold text-ink">{n}</div><div className="mt-1 text-sm">{label}</div>
          </Link>
        ))}
      </div>
      {data && data.recent.length > 0 && (
        <section className="mt-8 rounded-xl bg-canvas p-5 ring-1 ring-ink/10">
          <h2 className="text-lg font-bold">Recent activity</h2>
          <ul className="mt-3 divide-y divide-ink/10 text-sm">
            {data.recent.map((r, i) => <li key={i} className="flex justify-between py-2"><span>{r.action.replace(/_/g, ' ').toLowerCase()}{r.actor ? ` · ${r.actor}` : ''}</span><time className="text-body/70">{new Date(r.at).toLocaleString()}</time></li>)}
          </ul>
        </section>
      )}
    </div>
  )
}
