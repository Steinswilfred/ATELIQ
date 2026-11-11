import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Button } from '../../components/ui/Button'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'
import { EmptyState } from '../../components/ui/EmptyState'
import { Skeleton } from '../../components/ui/Skeleton'
import { ApiError, get, http } from '../../lib/api'
import { money } from '../../lib/format'
import type { CartItem, CartViewT } from '../../lib/orderTypes'
import { useAsync } from '../../lib/useAsync'
import { useCart } from '../../store/cartStore'
import { toast } from '../../store/toastStore'

export default function Cart() {
  const { data, loading, error, reload } = useAsync(() => get<CartViewT>('/me/cart'), [])
  const refresh = useCart((s) => s.refresh)
  const navigate = useNavigate()
  const [del, setDel] = useState<CartItem | null>(null)
  const done = async () => { reload(); await refresh() }
  const setQty = async (i: CartItem, q: number) => { try { await http.put(`/me/cart/items/${i.itemId}`, { quantity: q }); await done() } catch (e) { toast.error((e as ApiError).message) } }
  const remove = async () => { try { await http.delete(`/me/cart/items/${del!.itemId}`); toast.success('Removed from your cart.'); await done() } catch (e) { toast.error((e as ApiError).message) } setDel(null) }

  return (
    <div className="mx-auto max-w-5xl px-4 py-10">
      <h1 className="text-4xl font-bold">Your cart</h1>
      <div className="mt-8">
        {error ? <EmptyState title="Could not load your cart" hint={error.message} /> : loading ? <Skeleton className="h-48" /> : data!.items.length === 0
          ? <EmptyState title="Your cart is empty" hint="Customize a style and add it here." action={<Link to="/shop"><Button>Browse styles</Button></Link>} />
          : (<div className="grid gap-8 lg:grid-cols-[1fr_20rem]">
            <ul className="space-y-4">{data!.items.map((i) => (
              <li key={i.itemId} className="rounded-xl bg-canvas p-4 ring-1 ring-ink/10"><div className="flex gap-4">
                <div className="h-24 w-20 shrink-0 overflow-hidden rounded-lg bg-surface">{i.imageUrl && <img src={i.imageUrl} alt="" className="h-full w-full object-cover" />}</div>
                <div className="min-w-0 flex-1">
                  <div className="flex justify-between gap-2"><h2 className="font-bold text-ink">{i.name}</h2><span className="font-semibold text-ink">{money(i.lineTotal)}</span></div>
                  <p className="mt-1 text-xs">{[i.details.fabric, i.details.color, i.configuration.sizeCode && `Size ${i.configuration.sizeCode}`, i.configuration.measurementProfileId && 'Custom measurements', ...i.details.selections.map((s) => `${s.group}: ${s.option}`)].filter(Boolean).join(' · ')}</p>
                  <p className="mt-1 text-xs">{money(i.unitPrice)} each</p>
                  {i.problems.map((p) => <p key={p} className="mt-1 text-sm font-semibold text-primary" role="alert">{p}</p>)}
                  <div className="mt-3 flex flex-wrap items-center gap-4 text-sm">
                    {i.available && <div className="flex items-center gap-2" role="group" aria-label={`Quantity for ${i.name}`}><Button variant="ghost" className="px-3 py-1" disabled={i.quantity <= 1} onClick={() => setQty(i, i.quantity - 1)} aria-label="Decrease">−</Button><span className="w-6 text-center font-semibold">{i.quantity}</span><Button variant="ghost" className="px-3 py-1" disabled={i.quantity >= 10} onClick={() => setQty(i, i.quantity + 1)} aria-label="Increase">+</Button></div>}
                    {i.available && <Link className="font-semibold text-primary" to={`/product/${i.slug}?cartItem=${i.itemId}`}>Edit customization</Link>}
                    <button className="font-semibold text-ink/70 hover:text-primary" onClick={() => setDel(i)}>Remove</button></div>
                </div></div></li>))}</ul>
            <aside className="h-fit rounded-2xl bg-surface p-5"><h2 className="text-lg font-bold">Summary</h2>
              <dl className="mt-3 space-y-1 text-sm"><div className="flex justify-between"><dt>{data!.garmentCount} garment{data!.garmentCount === 1 ? '' : 's'}</dt><dd className="font-semibold text-ink">{money(data!.subtotal)}</dd></div></dl>
              <p className="mt-2 text-xs">Tailor stitching, coupons, tax and delivery are added at checkout.</p>
              <Button className="mt-4 w-full" disabled={!data!.checkoutReady} onClick={() => navigate('/checkout')}>Continue to checkout</Button>
              {!data!.checkoutReady && <p className="mt-2 text-xs">Fix the items marked above to continue.</p>}</aside>
          </div>)}
      </div>
      <ConfirmDialog open={!!del} danger title="Remove this item?" message={del ? `“${del.name}” will be removed from your cart.` : ''} confirmLabel="Remove" onConfirm={remove} onCancel={() => setDel(null)} />
    </div>
  )
}
