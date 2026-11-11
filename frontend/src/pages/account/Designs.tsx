import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Button } from '../../components/ui/Button'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'
import { EmptyState } from '../../components/ui/EmptyState'
import { Skeleton } from '../../components/ui/Skeleton'
import { ApiError, get, http } from '../../lib/api'
import type { DesignSummary } from '../../lib/shopTypes'
import { useAsync } from '../../lib/useAsync'
import { toast } from '../../store/toastStore'

export default function Designs() {
  const { data, loading, error, reload } = useAsync(() => get<DesignSummary[]>('/me/designs'), [])
  const [del, setDel] = useState<DesignSummary | null>(null)
  const remove = async () => { try { await http.delete(`/me/designs/${del!.id}`); toast.success('Design deleted.'); reload() } catch (e) { toast.error((e as ApiError).message) } setDel(null) }
  return (
    <div>
      <h1 className="text-3xl font-bold">Saved designs</h1>
      <div className="mt-6">
        {error ? <EmptyState title="Could not load your designs" hint={error.message} /> : loading ? <Skeleton className="h-40" /> : data!.length === 0
          ? <EmptyState title="No saved designs yet" hint="Customize any style and choose “Save this design” to keep it here." action={<Link to="/shop"><Button>Browse styles</Button></Link>} />
          : <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">{data!.map((d) => (
            <li key={d.id} className="overflow-hidden rounded-xl bg-canvas ring-1 ring-ink/10">
              <div className="aspect-[4/3] bg-surface">{d.imageUrl && <img src={d.imageUrl} alt="" className="h-full w-full object-cover" />}</div>
              <div className="p-4"><h2 className="font-bold text-ink">{d.name}</h2><p className="text-xs">{d.productName} · saved {new Date(d.updatedAt).toLocaleDateString()}</p>
                {!d.available && <p className="mt-1 text-xs font-semibold text-primary">This product is no longer available.</p>}
                <div className="mt-3 flex gap-3 text-sm font-semibold">{d.available && <Link className="text-primary" to={`/product/${d.productSlug}?design=${d.id}`}>Open</Link>}<button className="text-ink/70 hover:text-primary" onClick={() => setDel(d)}>Delete</button></div></div>
            </li>))}</ul>}
      </div>
      <ConfirmDialog open={!!del} danger title={`Delete “${del?.name}”?`} message="This cannot be undone." confirmLabel="Delete design" onConfirm={remove} onCancel={() => setDel(null)} />
    </div>
  )
}
