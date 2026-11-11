import { useEffect, useRef, useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { Button } from '../../components/ui/Button'
import { EmptyState } from '../../components/ui/EmptyState'
import { Skeleton } from '../../components/ui/Skeleton'
import { ApiError, get, post } from '../../lib/api'
import { money } from '../../lib/format'
import type { CheckoutResultT, OrderDetailT } from '../../lib/orderTypes'
import { payWithRazorpay } from '../../lib/razorpay'
import { toast } from '../../store/toastStore'

/** Shows what the SERVER says about the payment, never what the browser reported. While it is unconfirmed we keep checking for a short while. */
export default function PaymentResult() {
  const { orderId } = useParams()
  const [params, setParams] = useSearchParams()
  const [order, setOrder] = useState<OrderDetailT | null>(null)
  const [error, setError] = useState<ApiError | null>(null)
  const [retrying, setRetrying] = useState(false)
  const tries = useRef(0)

  useEffect(() => {
    let live = true; let timer: ReturnType<typeof setTimeout>
    const load = async () => {
      try {
        const o = await get<OrderDetailT>(`/me/orders/${orderId}`)
        if (!live) return
        setOrder(o)
        if (o.paymentMethod === 'RAZORPAY' && o.paymentStatus === 'PENDING' && params.get('outcome') !== 'DISMISSED' && tries.current++ < 10) timer = setTimeout(load, 3000)
      } catch (e) { if (live) setError(e as ApiError) }
    }
    void load()
    return () => { live = false; clearTimeout(timer) }
  }, [orderId, params])

  const retry = async () => {
    setRetrying(true)
    try {
      const r = await post<CheckoutResultT>(`/me/orders/${orderId}/pay`)
      if (r.razorpay) { const outcome = await payWithRazorpay(r.razorpay); tries.current = 0; setParams({ outcome }) }
    } catch (e) { toast.error((e as ApiError).message) } finally { setRetrying(false) }
  }

  if (error) return <div className="mx-auto max-w-2xl px-4 py-16"><EmptyState title="We could not load this order" hint={error.message} action={<Link to="/account/orders"><Button variant="ghost">My orders</Button></Link>} /></div>
  if (!order) return <div className="mx-auto max-w-2xl px-4 py-16"><Skeleton className="h-48" /></div>
  const paid = order.paymentStatus === 'PAID', cod = order.paymentStatus === 'PENDING_COLLECTION', failed = order.paymentStatus === 'FAILED'
  const waiting = order.paymentMethod === 'RAZORPAY' && order.paymentStatus === 'PENDING' && params.get('outcome') !== 'DISMISSED'
  const title = paid ? 'Payment received' : cod ? 'Order placed' : failed ? 'Payment did not go through' : waiting ? 'Confirming your payment…' : 'Payment not completed'
  const body = paid ? `Thank you. Order ${order.orderNumber} is confirmed and we are finding a tailor.` : cod ? `Order ${order.orderNumber} is placed. Please keep ${money(order.total)} ready to pay in cash on delivery.`
    : failed ? 'Your bank or card did not complete the payment. You have not been charged for this attempt. You can try again.' : waiting ? 'This usually takes a few seconds. You can leave this page; we will confirm by notification.' : 'The payment window was closed before payment finished. Your order is saved; you can pay now or later from My orders.'

  return (
    <div className="mx-auto max-w-2xl px-4 py-16 text-center" role="status" aria-live="polite">
      <div className={`mx-auto grid h-16 w-16 place-items-center rounded-full text-3xl text-white ${paid || cod ? 'bg-emerald-600' : failed ? 'bg-primary' : 'bg-ink'}`} aria-hidden>{paid || cod ? '✓' : failed ? '!' : '…'}</div>
      <h1 className="mt-6 text-3xl font-bold">{title}</h1><p className="mt-3 text-sm">{body}</p>
      <p className="mt-2 text-sm font-semibold text-ink">Order {order.orderNumber} · {money(order.total)}</p>
      <div className="mt-8 flex flex-wrap justify-center gap-3">
        {!paid && !cod && order.status === 'PAYMENT_PENDING' && order.paymentMethod === 'RAZORPAY' && !(waiting && tries.current < 10) && <Button loading={retrying} onClick={retry}>Pay {money(order.total)} now</Button>}
        <Link to={`/account/orders/${order.id}`}><Button variant="ghost">View order</Button></Link><Link to="/shop"><Button variant="ghost">Keep shopping</Button></Link>
      </div>
    </div>
  )
}
