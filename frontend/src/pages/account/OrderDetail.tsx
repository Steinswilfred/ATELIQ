import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { OrderView } from '../../components/shop/OrderView'
import { CancelOrderPanel, RefundsPanel } from '../../components/shop/CancelRefundPanels'
import { DeliveryPanel } from '../../components/shop/DeliveryPanel'
import { SamplePanel } from '../../components/shop/SamplePanel'
import { TrackingStepper } from '../../components/shop/TrackingStepper'
import { Button } from '../../components/ui/Button'
import { EmptyState } from '../../components/ui/EmptyState'
import { Skeleton } from '../../components/ui/Skeleton'
import { ApiError, get, http, post } from '../../lib/api'
import { CheckoutResultT, OrderDetailT, statusLabel } from '../../lib/orderTypes'
import { payWithRazorpay } from '../../lib/razorpay'
import { useAsync } from '../../lib/useAsync'
import { toast } from '../../store/toastStore'

/** Fetches the invoice with the sign-in token, then shows it in a new tab (a plain link could not send the token). */
export async function openInvoice(path: string) {
  try {
    const r = await http.get<string>(path, { responseType: 'text', transformResponse: (d) => d })
    const url = URL.createObjectURL(new Blob([r.data], { type: 'text/html' }))
    window.open(url, '_blank', 'noopener'); setTimeout(() => URL.revokeObjectURL(url), 60000)
  } catch (e) {
    const raw = (e as { response?: { data?: string } })?.response?.data
    let msg = (e as ApiError).message
    try { msg = JSON.parse(raw ?? '{}').error?.message ?? msg } catch { /* keep message */ }
    toast.error(msg)
  }
}

export default function OrderDetail() {
  const { id } = useParams()
  const navigate = useNavigate()
  const { data, loading, error, reload } = useAsync(() => get<OrderDetailT>(`/me/orders/${id}`), [id])
  const [busy, setBusy] = useState(false)
  // refresh the tracking every 30 seconds while the order is in progress (live push arrives with WebSockets)
  const finished = data && ['DELIVERED', 'CANCELLED', 'REFUNDED'].includes(data.status)
  useEffect(() => { if (!data || finished) return; const t = setInterval(reload, 30000); return () => clearInterval(t) }, [data, finished, reload])
  const payNow = async () => {
    setBusy(true)
    try { const r = await post<CheckoutResultT>(`/me/orders/${id}/pay`); if (r.razorpay) { const outcome = await payWithRazorpay(r.razorpay); navigate(`/checkout/result/${id}?outcome=${outcome}`) } }
    catch (e) { toast.error((e as ApiError).message) } finally { setBusy(false) }
  }
  if (loading) return <Skeleton className="h-64" />
  if (error || !data) return <EmptyState title="Order not found" hint={error?.message} action={<Link to="/account/orders"><Button variant="ghost">My orders</Button></Link>} />
  return (
    <div className="space-y-6">
      <Link to="/account/orders" className="text-sm font-semibold text-primary">← My orders</Link>
      <div className="flex flex-wrap items-start justify-between gap-3"><div><h1 className="text-3xl font-bold">{data.orderNumber}</h1><p className="mt-1 text-sm">Placed {new Date(data.placedAt).toLocaleString()} · <strong>{statusLabel(data.status)}</strong></p></div>
        <div className="flex flex-wrap gap-2">
          {data.status === 'PAYMENT_PENDING' && data.paymentMethod === 'RAZORPAY' && <Button loading={busy} onClick={payNow}>Pay now</Button>}
          {data.invoiceNumber && <Button variant="ghost" onClick={() => openInvoice(`/me/orders/${data.id}/invoice`)}>Invoice</Button>}</div></div>
      <TrackingStepper status={data.status} timeline={data.timeline} />
      <SamplePanel viewer="customer" orderId={data.id} orderStatus={data.status} onChanged={reload} />
      <DeliveryPanel viewer="customer" orderId={data.id} orderStatus={data.status} />
      <RefundsPanel orderId={data.id} status={data.status} />
      <OrderView o={data} />
      <CancelOrderPanel orderId={data.id} status={data.status} onChanged={reload} />
      <Button variant="ghost" onClick={reload}>Refresh status</Button>
    </div>
  )
}
