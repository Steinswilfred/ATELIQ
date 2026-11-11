import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { AddressForm } from '../../components/shop/AddressForm'
import { Button } from '../../components/ui/Button'
import { EmptyState } from '../../components/ui/EmptyState'
import { Skeleton } from '../../components/ui/Skeleton'
import { ApiError, get, post } from '../../lib/api'
import { money } from '../../lib/format'
import type { AddressT, CheckoutResultT, Preview } from '../../lib/orderTypes'
import { payWithRazorpay } from '../../lib/razorpay'
import type { TailorCategoryT } from '../../lib/shopTypes'
import { useAsync } from '../../lib/useAsync'
import { useCart } from '../../store/cartStore'
import { toast } from '../../store/toastStore'

const radio = (on: boolean) => `w-full rounded-lg p-3 text-left ring-1 transition ${on ? 'bg-ink text-white ring-ink' : 'bg-canvas ring-ink/20 hover:ring-primary'}`

export default function Checkout() {
  const navigate = useNavigate()
  const refreshCart = useCart((c) => c.refresh)
  const addresses = useAsync(() => get<AddressT[]>('/me/addresses'), [])
  const tailors = useAsync(() => get<TailorCategoryT[]>('/public/tailor-categories').catch(() => [] as TailorCategoryT[]), [])
  const [addressId, setAddressId] = useState<number | null>(null)
  const [addingAddress, setAddingAddress] = useState(false)
  const [tailorId, setTailorId] = useState<number | null>(null)
  const [coupon, setCoupon] = useState(''); const [appliedCoupon, setAppliedCoupon] = useState('')
  const [instructions, setInstructions] = useState('')
  const [method, setMethod] = useState<'RAZORPAY' | 'COD' | ''>('')
  const [preview, setPreview] = useState<Preview | null>(null)
  const [previewing, setPreviewing] = useState(true)
  const [placing, setPlacing] = useState(false)

  useEffect(() => { if (addressId === null && addresses.data?.length) setAddressId((addresses.data.find((a) => a.isDefault) ?? addresses.data[0]).id) }, [addresses.data, addressId])
  useEffect(() => {
    setPreviewing(true)
    const t = setTimeout(() => { post<Preview>('/me/checkout/preview', { tailorCategoryId: tailorId, couponCode: appliedCoupon || null }).then(setPreview).catch(() => setPreview(null)).finally(() => setPreviewing(false)) }, 150)
    return () => clearTimeout(t)
  }, [tailorId, appliedCoupon])
  useEffect(() => { if (!method && preview) setMethod(preview.onlineEnabled ? 'RAZORPAY' : preview.codEnabled ? 'COD' : '') }, [preview, method])

  const couponError = useMemo(() => preview?.errors.find((e) => /coupon/i.test(e)), [preview])
  const otherErrors = (preview?.errors ?? []).filter((e) => !/coupon/i.test(e) && !/tailor category/i.test(e))
  const canPlace = !!preview && preview.canCheckout && !couponError && addressId !== null && !!method && !placing

  const place = async () => {
    if (!canPlace) return
    setPlacing(true)
    try {
      const r = await post<CheckoutResultT>('/me/checkout', { addressId, tailorCategoryId: tailorId, couponCode: appliedCoupon || null, paymentMethod: method, deliveryInstructions: instructions || null })
      await refreshCart()
      if (r.paymentMethod === 'COD' || !r.razorpay) { navigate(`/checkout/result/${r.orderId}`); return }
      const outcome = await payWithRazorpay(r.razorpay)
      navigate(`/checkout/result/${r.orderId}?outcome=${outcome}`)
    } catch (e) { toast.error((e as ApiError).message) } finally { setPlacing(false) }
  }

  if (preview && preview.items.length === 0 && !previewing) return <div className="mx-auto max-w-3xl px-4 py-16"><EmptyState title="Your cart is empty" action={<Link to="/shop"><Button>Browse styles</Button></Link>} /></div>
  const t = preview?.totals
  const Row = ({ k, v, neg }: { k: string; v: number; neg?: boolean }) => <div className="flex justify-between"><dt>{k}</dt><dd>{neg ? '−' : ''}{money(v)}</dd></div>

  return (
    <div className="mx-auto grid max-w-6xl gap-10 px-4 py-10 lg:grid-cols-[1fr_22rem]">
      <div className="space-y-10">
        <h1 className="text-4xl font-bold">Checkout</h1>

        <section><h2 className="text-xl font-bold">1. Delivery address</h2>
          {addresses.loading ? <Skeleton className="mt-3 h-20" /> : addingAddress || (addresses.data ?? []).length === 0 ? (
            <div className="mt-3 rounded-xl bg-canvas p-5 ring-1 ring-ink/10"><AddressForm onCancel={() => setAddingAddress(false)} onSaved={(id) => { setAddingAddress(false); addresses.reload(); if (id) setAddressId(id) }} /></div>
          ) : (<div className="mt-3 grid gap-3 sm:grid-cols-2" role="radiogroup" aria-label="Delivery address">
            {addresses.data!.map((a) => <button key={a.id} type="button" role="radio" aria-checked={addressId === a.id} onClick={() => setAddressId(a.id)} className={radio(addressId === a.id)}>
              <div className="text-sm font-semibold">{a.label ? `${a.label} · ` : ''}{a.fullName}</div><div className="mt-1 text-xs opacity-80">{a.line1}{a.line2 ? `, ${a.line2}` : ''}, {a.city}, {a.state} {a.postalCode}<br />{a.phone}</div></button>)}
            <button type="button" className="rounded-lg p-3 text-sm font-semibold text-primary ring-1 ring-dashed ring-primary/50" onClick={() => setAddingAddress(true)}>+ Add a new address</button></div>)}
          <label className="mt-4 block text-sm font-semibold text-ink">Delivery instructions (optional)<textarea rows={2} maxLength={500} value={instructions} onChange={(e) => setInstructions(e.target.value)} className="mt-1 w-full rounded-lg border border-ink/20 px-3 py-2 text-sm font-normal" /></label>
        </section>

        <section><h2 className="text-xl font-bold">2. Tailor</h2><p className="text-xs">Choose the skill level of the tailor who will stitch all garments in this order. Each garment is charged the amount shown.</p>
          <div className="mt-3 grid gap-3 sm:grid-cols-2" role="radiogroup" aria-label="Tailor category">{(tailors.data ?? []).map((c) => (
            <button key={c.id} type="button" role="radio" aria-checked={tailorId === c.id} onClick={() => setTailorId(c.id)} className={radio(tailorId === c.id)}><div className="flex justify-between text-sm font-semibold"><span>{c.name}</span><span>{money(c.stitchingPrice)} / garment</span></div>{c.description && <div className="mt-1 text-xs opacity-80">{c.description}</div>}</button>))}</div>
          {(tailors.data ?? []).length === 0 && !tailors.loading && <p className="mt-2 text-sm font-semibold text-primary">No tailor categories are available right now. Please try again later.</p>}
        </section>

        <section><h2 className="text-xl font-bold">3. Coupon</h2>
          <form className="mt-3 flex gap-2" onSubmit={(e) => { e.preventDefault(); setAppliedCoupon(coupon.trim()) }}>
            <input value={coupon} onChange={(e) => setCoupon(e.target.value)} placeholder="Coupon code" aria-label="Coupon code" className="w-56 rounded-lg border border-ink/20 px-3 py-2 text-sm" />
            <Button type="submit" variant="ghost">Apply</Button>{appliedCoupon && <Button type="button" variant="ghost" onClick={() => { setAppliedCoupon(''); setCoupon('') }}>Remove</Button>}</form>
          {couponError && <p role="alert" className="mt-2 text-sm font-semibold text-primary">{couponError}</p>}
          {preview?.couponCode && !couponError && <p className="mt-2 text-sm font-semibold text-emerald-700">Coupon {preview.couponCode} applied.</p>}
        </section>

        <section><h2 className="text-xl font-bold">4. Payment</h2>
          <div className="mt-3 grid gap-3 sm:grid-cols-2" role="radiogroup" aria-label="Payment method">
            <button type="button" role="radio" aria-checked={method === 'RAZORPAY'} disabled={!preview?.onlineEnabled} onClick={() => setMethod('RAZORPAY')} className={`${radio(method === 'RAZORPAY')} disabled:opacity-50`}><div className="text-sm font-semibold">Pay online</div><div className="mt-1 text-xs opacity-80">{preview && !preview.onlineEnabled ? 'Not available right now' : 'UPI, cards, net banking and more via Razorpay'}</div></button>
            <button type="button" role="radio" aria-checked={method === 'COD'} disabled={!preview?.codEnabled} onClick={() => setMethod('COD')} className={`${radio(method === 'COD')} disabled:opacity-50`}><div className="text-sm font-semibold">Cash on delivery</div><div className="mt-1 text-xs opacity-80">{preview && !preview.codEnabled ? 'Not available right now' : 'Pay in cash when your order arrives'}</div></button></div>
        </section>
      </div>

      <aside className="h-fit space-y-3 rounded-2xl bg-surface p-5 lg:sticky lg:top-24" aria-live="polite">
        <h2 className="text-lg font-bold">Order summary</h2>
        {previewing && !preview ? <Skeleton className="h-40" /> : preview && t && (<>
          <ul className="space-y-1 text-sm">{preview.items.map((i) => <li key={i.itemId} className="flex justify-between gap-2"><span className="truncate">{i.name} × {i.quantity}</span><span>{money(i.lineTotal)}</span></li>)}</ul>
          <dl className="space-y-1 border-t border-ink/10 pt-3 text-sm"><Row k="Items" v={t.subtotal} />{t.discount > 0 && <Row k={`Discount${preview.couponCode ? ` (${preview.couponCode})` : ''}`} v={t.discount} neg />}
            <Row k={preview.tailorCategory ? `Stitching · ${preview.tailorCategory} × ${preview.garments}` : 'Stitching (choose a tailor)'} v={t.tailorCharges} /><Row k={`Tax (${t.taxRatePercent}%)`} v={t.tax} /><Row k="Delivery" v={t.delivery} />
            <div className="flex justify-between border-t border-ink/10 pt-2 text-lg font-bold text-ink"><dt>Total</dt><dd>{money(t.total)}</dd></div></dl>
          {otherErrors.map((e) => <p key={e} role="alert" className="text-sm font-semibold text-primary">{e}</p>)}
          {!tailorId && <p className="text-xs">Choose a tailor category to continue.</p>}
          <Button className="w-full" loading={placing} disabled={!canPlace} onClick={place}>{method === 'COD' ? 'Place order' : 'Pay now'} · {money(t.total)}</Button>
          <p className="text-xs">The amount is calculated on our servers from current prices. Card details are entered only in Razorpay’s secure window.</p></>)}
        <Link to="/cart" className="block text-sm font-semibold text-primary">← Back to cart</Link>
      </aside>
    </div>
  )
}
