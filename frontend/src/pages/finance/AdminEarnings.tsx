import { useState } from 'react'
import { Button } from '../../components/ui/Button'
import { EmptyState } from '../../components/ui/EmptyState'
import { FormDialog } from '../../components/ui/FormDialog'
import { Skeleton } from '../../components/ui/Skeleton'
import { ApiError, get, post } from '../../lib/api'
import { money } from '../../lib/format'
import { useAsync } from '../../lib/useAsync'
import { toast } from '../../store/toastStore'

interface Balance { tailorUserId: number; name: string; pending: number; payable: number; payableOrders: number; recoveryFlags: number }
interface Payout { id: number; tailorName: string; amount: number; reference: string; note?: string; paidBy: string; paidAt: string }

export default function AdminEarnings() {
  const balances = useAsync(() => get<Balance[]>('/admin/earnings/balances'), [])
  const payouts = useAsync(() => get<Payout[]>('/admin/payouts'), [])
  const [pay, setPay] = useState<Balance | null>(null)
  if (balances.error) return <EmptyState title="Could not load earnings" hint={balances.error.message} />
  return (
    <div className="max-w-5xl space-y-8">
      <div><h1 className="text-3xl font-bold">Earnings & payouts</h1>
        <p className="mt-1 text-sm">A tailor’s earning (stitching charge minus commission) is created when an order is delivered and becomes payable after the settlement hold. <strong>Pay the tailor by bank transfer first, then record it here.</strong> This system does not move money itself.</p></div>
      <section><h2 className="text-xl font-bold">Balances</h2>
        <div className="mt-3 overflow-x-auto rounded-xl bg-canvas ring-1 ring-ink/10">{balances.loading ? <Skeleton className="m-4 h-24" /> : balances.data!.length === 0 ? <EmptyState title="No earnings yet" hint="Earnings appear after the first delivery." />
          : <table className="w-full text-left text-sm"><thead className="border-b border-ink/10 text-xs text-body/70"><tr><th className="p-3">Tailor</th><th>In hold period</th><th>Payable now</th><th>Orders</th><th /></tr></thead>
            <tbody>{balances.data!.map((b) => <tr key={b.tailorUserId} className="border-b border-ink/5"><td className="p-3 font-semibold text-ink">{b.name}{b.recoveryFlags > 0 && <div className="text-xs font-normal text-primary">{b.recoveryFlags} refunded order(s) already paid out: recover manually</div>}</td><td>{money(b.pending)}</td><td className="font-semibold text-ink">{money(b.payable)}</td><td>{b.payableOrders}</td>
              <td className="p-3 text-right">{b.payable > 0 && <Button onClick={() => setPay(b)}>Record payout</Button>}</td></tr>)}</tbody></table>}</div></section>
      <section><h2 className="text-xl font-bold">Payout history</h2>
        <div className="mt-3 overflow-x-auto rounded-xl bg-canvas ring-1 ring-ink/10">{payouts.loading ? <Skeleton className="m-4 h-24" /> : payouts.data!.length === 0 ? <p className="p-5 text-sm">No payouts recorded yet.</p>
          : <table className="w-full text-left text-sm"><thead className="border-b border-ink/10 text-xs text-body/70"><tr><th className="p-3">Tailor</th><th>Amount</th><th>Reference</th><th>Recorded by</th><th>Date</th></tr></thead>
            <tbody>{payouts.data!.map((p) => <tr key={p.id} className="border-b border-ink/5"><td className="p-3">{p.tailorName}</td><td className="font-semibold text-ink">{money(p.amount)}</td><td>{p.reference}{p.note && <div className="text-xs">{p.note}</div>}</td><td>{p.paidBy}</td><td>{new Date(p.paidAt).toLocaleString()}</td></tr>)}</tbody></table>}</div></section>
      <FormDialog open={!!pay} title={pay ? `Record payout for ${pay.name}` : ''} submitLabel="Record payout" onCancel={() => setPay(null)}
        fields={[{ name: 'reference', label: 'Bank transfer reference', required: true, hint: pay ? `This settles ${pay.payableOrders} order(s), ${money(pay.payable)} in total.` : '' }, { name: 'note', label: 'Note (optional)' }]}
        onSubmit={async (v) => { try { await post('/admin/payouts', { tailorUserId: pay!.tailorUserId, reference: v.reference, note: v.note || null }); toast.success('Payout recorded. The tailor was notified.'); setPay(null); balances.reload(); payouts.reload() } catch (e) { throw e as ApiError } }} />
    </div>
  )
}
