import { useEffect, useMemo, useState } from 'react'
import { Link, useLocation, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { Button } from '../../components/ui/Button'
import { EmptyState } from '../../components/ui/EmptyState'
import { PromptDialog } from '../../components/ui/PromptDialog'
import { PreviewPanel } from '../../components/shop/PreviewPanel'
import { Skeleton } from '../../components/ui/Skeleton'
import { ApiError, get, http, post } from '../../lib/api'
import { money } from '../../lib/format'
import type { DesignConfig, DesignSummary, MeasurementProfile, ProductT, Quote, TailorCategoryT } from '../../lib/shopTypes'
import { useAsync } from '../../lib/useAsync'
import type { CartViewT } from '../../lib/orderTypes'
import { useAuth } from '../../store/authStore'
import { useCart } from '../../store/cartStore'
import { toast } from '../../store/toastStore'

const chip = (on: boolean) => `rounded-lg px-4 py-2 text-sm font-semibold ring-1 transition ${on ? 'bg-ink text-white ring-ink' : 'bg-canvas text-ink ring-ink/20 hover:ring-primary'}`

export default function ProductPage() {
  const { slug } = useParams()
  const [params] = useSearchParams()
  const designId = params.get('design')
  const cartItemId = params.get('cartItem')
  const refreshCart = useCart((c) => c.refresh)
  const navigate = useNavigate()
  const location = useLocation()
  const user = useAuth((s) => s.user)
  const isCustomer = user?.userType === 'CUSTOMER'

  const product = useAsync(() => get<ProductT>(`/public/products/${slug}`), [slug])
  const profiles = useAsync(() => (isCustomer ? get<MeasurementProfile[]>('/me/measurements') : Promise.resolve([] as MeasurementProfile[])), [isCustomer])
  const saved = useAsync(() => (isCustomer && designId ? get<{ design: DesignSummary }>(`/me/designs/${designId}`) : Promise.resolve(null)), [isCustomer, designId])

  const cartView = useAsync(() => (isCustomer && cartItemId ? get<CartViewT>('/me/cart') : Promise.resolve(null)), [isCustomer, cartItemId])
  const tailors = useAsync(() => get<TailorCategoryT[]>('/public/tailor-categories').catch(() => [] as TailorCategoryT[]), [])
  const [tailorId, setTailorId] = useState<number | null>(null)
  const [fit, setFit] = useState<'size' | 'custom'>('size')
  const [sizeCode, setSizeCode] = useState<string | null>(null)
  const [profileId, setProfileId] = useState<number | null>(null)
  const [fabricId, setFabricId] = useState<number | null>(null)
  const [colorId, setColorId] = useState<number | null>(null)
  const [options, setOptions] = useState<Record<string, number>>({})
  const [quote, setQuote] = useState<Quote | null>(null)
  const [attempted, setAttempted] = useState(false)
  const [saveOpen, setSaveOpen] = useState(false)

  // defaults when the product loads: default option per group, first fabric and its first colour
  useEffect(() => {
    const p = product.data; if (!p) return
    const o: Record<string, number> = {}
    p.groups.forEach((g) => { const d = g.options.find((x) => x.isDefault); if (d) o[String(g.id)] = d.id })
    setOptions(o); setFabricId(p.fabrics[0]?.id ?? null); setColorId(p.fabrics[0]?.colors[0]?.id ?? null)
  }, [product.data])

  // a saved design overrides the defaults
  useEffect(() => {
    const c = saved.data?.design.configuration; if (!c) return
    setFit(c.measurementProfileId ? 'custom' : 'size'); setSizeCode(c.sizeCode); setProfileId(c.measurementProfileId)
    setFabricId(c.fabricId); setColorId(c.colorId); setTailorId(c.tailorCategoryId ?? null); setOptions(Object.fromEntries(Object.entries(c.options ?? {}).map(([k, v]) => [k, Number(v)])))
  }, [saved.data])

  // editing a cart line restores its choices
  useEffect(() => {
    const item = cartView.data?.items.find((i) => String(i.itemId) === cartItemId); const c = item?.configuration; if (!c) return
    setFit(c.measurementProfileId ? 'custom' : 'size'); setSizeCode(c.sizeCode); setProfileId(c.measurementProfileId)
    setFabricId(c.fabricId); setColorId(c.colorId); setOptions(Object.fromEntries(Object.entries(c.options ?? {}).map(([k, v]) => [k, Number(v)])))
  }, [cartView.data, cartItemId])

  const config: DesignConfig = useMemo(() => ({
    sizeCode: fit === 'size' ? sizeCode : null, measurementProfileId: fit === 'custom' ? profileId : null, fabricId, colorId, options, tailorCategoryId: tailorId,
  }), [fit, sizeCode, profileId, fabricId, colorId, options, tailorId])

  // the server decides the price; re-ask whenever the choices change
  useEffect(() => {
    const p = product.data; if (!p) return
    const t = setTimeout(() => { post<Quote>(`/public/products/${p.id}/quote`, config).then(setQuote).catch(() => setQuote(null)) }, 200)
    return () => clearTimeout(t)
  }, [config, product.data])

  const fabric = product.data?.fabrics.find((f) => f.id === fabricId)
  const missingFit = !(config.sizeCode || config.measurementProfileId)
  const problems = [...(missingFit ? ['Choose a size or one of your measurement profiles.'] : []), ...(quote?.errors ?? []).filter((e) => !e.startsWith('Choose a size or one'))]

  const requireSignIn = () => { navigate('/login', { state: { from: location.pathname + location.search } }) }
  const trySave = () => {
    if (!user) return requireSignIn()
    setAttempted(true)
    if (problems.length > 0) { toast.error(problems[0]); return }
    if (saved.data) void persist(saved.data.design.name)
    else setSaveOpen(true)
  }
  const addToCart = async () => {
    if (!user) return requireSignIn()
    setAttempted(true)
    if (problems.length > 0) { toast.error(problems[0]); return }
    try {
      if (cartItemId) { await http.put(`/me/cart/items/${cartItemId}`, { configuration: config }); toast.success('Cart item updated.') }
      else { await post('/me/cart/items', { productId: product.data!.id, quantity: 1, configuration: config }); toast.success('Added to your cart.') }
      await refreshCart(); navigate('/cart')
    } catch (e) { toast.error((e as ApiError).message) }
  }
  const persist = async (name: string) => {
    const p = product.data!
    try {
      if (saved.data) { await http.put(`/me/designs/${saved.data.design.id}`, { productId: p.id, name, configuration: config }); toast.success('Design updated.') }
      else { await post('/me/designs', { productId: p.id, name, configuration: config }); toast.success('Design saved. Find it under Saved designs.') }
      setSaveOpen(false)
    } catch (e) { toast.error((e as ApiError).message) }
  }

  if (product.loading) return <div className="mx-auto grid max-w-7xl gap-8 px-4 py-10 md:grid-cols-2"><Skeleton className="aspect-[4/5]" /><div className="space-y-3"><Skeleton className="h-10 w-2/3" /><Skeleton className="h-40" /></div></div>
  if (product.error || !product.data) return <div className="mx-auto max-w-3xl px-4 py-16"><EmptyState title="This product is not available" hint={product.error?.message} action={<Link to="/shop"><Button variant="ghost">Browse all styles</Button></Link>} /></div>
  const p = product.data

  return (
    <div className="mx-auto grid max-w-7xl gap-10 px-4 py-10 md:grid-cols-2">
      <PreviewPanel product={p} config={config} measurementProfile={profiles.data?.find((profile) => profile.id === profileId)} />

      <div>
        <p className="text-sm font-semibold text-primary">{p.sectionName} › {p.categoryName}</p>
        <h1 className="mt-1 text-4xl font-bold">{p.name}</h1>
        {p.description && <p className="mt-3 whitespace-pre-line text-sm">{p.description}</p>}
        {saved.data && <p className="mt-3 rounded-lg bg-surface p-3 text-sm">Editing your saved design “{saved.data.design.name}”.</p>}

        {p.fabrics.length > 0 && <section className="mt-8"><h2 className="text-lg font-bold">Fabric</h2>
          <div className="mt-3 flex flex-wrap gap-2">{p.fabrics.map((f) => <button key={f.id} onClick={() => { setFabricId(f.id); setColorId(f.colors[0]?.id ?? null) }} aria-pressed={fabricId === f.id} className={chip(fabricId === f.id)}>{f.name}{f.priceDelta > 0 && <span className="ml-1 text-xs opacity-80">+{money(f.priceDelta)}</span>}</button>)}</div>
          {fabric?.material && <p className="mt-2 text-xs">{fabric.material}</p>}
          {fabric && fabric.colors.length > 0 && <div className="mt-4"><h3 className="text-sm font-bold">Colour{colorId ? `: ${fabric.colors.find((c) => c.id === colorId)?.name ?? ''}` : ''}</h3>
            <div className="mt-2 flex flex-wrap gap-2">{fabric.colors.map((c) => <button key={c.id} onClick={() => setColorId(c.id)} aria-label={c.name} aria-pressed={colorId === c.id} className={`h-9 w-9 rounded-full ring-2 ring-offset-2 ${colorId === c.id ? 'ring-primary' : 'ring-ink/20'}`} style={{ background: c.hexCode }} />)}</div></div>}
        </section>}

        {p.groups.map((g) => (
          <fieldset key={g.id} className="mt-8"><legend className="text-lg font-bold">{g.name}{g.required ? '' : ' (optional)'}</legend>
            {g.description && <p className="text-xs">{g.description}</p>}
            <div className="mt-3 flex flex-wrap gap-2">
              {!g.required && <button type="button" aria-pressed={!options[String(g.id)]} className={chip(!options[String(g.id)])} onClick={() => setOptions((o) => { const n = { ...o }; delete n[String(g.id)]; return n })}>None</button>}
              {g.options.map((o) => <button type="button" key={o.id} aria-pressed={options[String(g.id)] === o.id} className={chip(options[String(g.id)] === o.id)} onClick={() => setOptions((x) => ({ ...x, [String(g.id)]: o.id }))}>{o.name}{o.priceDelta > 0 && <span className="ml-1 text-xs opacity-80">+{money(o.priceDelta)}</span>}</button>)}
            </div>
          </fieldset>))}

        <section className="mt-8"><h2 className="text-lg font-bold">Fit</h2>
          <div className="mt-3 flex gap-2" role="group" aria-label="Fit type"><button className={chip(fit === 'size')} aria-pressed={fit === 'size'} onClick={() => setFit('size')}>Standard size</button>
            {isCustomer && <button className={chip(fit === 'custom')} aria-pressed={fit === 'custom'} onClick={() => setFit('custom')}>My measurements</button>}</div>
          {fit === 'size' ? (
            <div className="mt-3 flex flex-wrap gap-2">{p.sizes.length === 0 ? <p className="text-sm">No standard sizes are listed for this product.</p> : p.sizes.map((s) => <button key={s.code} aria-pressed={sizeCode === s.code} title={s.name} className={chip(sizeCode === s.code)} onClick={() => setSizeCode(s.code)}>{s.code}</button>)}</div>
          ) : (
            <div className="mt-3">{(profiles.data ?? []).length === 0 ? <p className="text-sm">You have no saved measurements yet. <Link to="/account/measurements" className="font-semibold text-primary">Add a measurement profile</Link></p>
              : <select aria-label="Measurement profile" value={profileId ?? ''} onChange={(e) => setProfileId(e.target.value ? Number(e.target.value) : null)} className="rounded-lg border border-ink/20 bg-canvas px-3 py-2 text-sm"><option value="">Choose a profile…</option>{profiles.data!.map((m) => <option key={m.id} value={m.id}>{m.name}{m.isDefault ? ' (default)' : ''}</option>)}</select>}</div>)}
          {!user && <p className="mt-2 text-xs"><Link to="/login" className="font-semibold text-primary">Sign in</Link> to use saved measurements and save designs.</p>}
        </section>

        {(tailors.data ?? []).length > 0 && <section className="mt-8"><h2 className="text-lg font-bold">Tailor</h2>
          <p className="text-xs">Choose the skill level of the tailor who will stitch your garment. The price is set by us and added per garment. You can decide later at checkout.</p>
          <div className="mt-3 grid gap-2 sm:grid-cols-2" role="radiogroup" aria-label="Tailor category">
            <button type="button" role="radio" aria-checked={tailorId === null} onClick={() => setTailorId(null)} className={`rounded-lg p-3 text-left ring-1 ${tailorId === null ? 'bg-ink text-white ring-ink' : 'bg-canvas ring-ink/20 hover:ring-primary'}`}><div className="text-sm font-semibold">Decide at checkout</div></button>
            {tailors.data!.map((t) => <button type="button" key={t.id} role="radio" aria-checked={tailorId === t.id} onClick={() => setTailorId(t.id)} className={`rounded-lg p-3 text-left ring-1 ${tailorId === t.id ? 'bg-ink text-white ring-ink' : 'bg-canvas ring-ink/20 hover:ring-primary'}`}>
              <div className="flex justify-between text-sm font-semibold"><span>{t.name}</span><span>{money(t.stitchingPrice)}</span></div>{t.description && <div className="mt-1 text-xs opacity-80">{t.description}</div>}</button>)}
          </div></section>}

        <section className="mt-8 rounded-2xl bg-surface p-5" aria-live="polite">
          <h2 className="text-lg font-bold">Your price</h2>
          <dl className="mt-3 space-y-1 text-sm">
            <div className="flex justify-between"><dt>Base price</dt><dd>{money(quote?.basePrice ?? p.basePrice)}</dd></div>
            {quote?.lines.map((l) => <div key={l.label} className="flex justify-between"><dt>{l.label}</dt><dd>+{money(l.amount)}</dd></div>)}
            <div className="flex justify-between border-t border-ink/10 pt-2 text-lg font-bold text-ink"><dt>Total so far</dt><dd>{money(quote?.total ?? p.basePrice)}</dd></div>
          </dl>
          <p className="mt-2 text-xs">{tailorId ? 'Includes the stitching charge for your chosen tailor category. ' : 'Tailor stitching charge is added once you choose a category. '}Delivery and taxes are added at checkout.</p>
          {attempted && problems.length > 0 && <ul className="mt-3 list-disc pl-5 text-sm font-semibold text-primary" role="alert">{problems.map((e) => <li key={e}>{e}</li>)}</ul>}
          <div className="mt-4 flex flex-wrap gap-3">
            {(!user || isCustomer) && <Button onClick={trySave}>{saved.data ? 'Update saved design' : 'Save this design'}</Button>}
            {(!user || isCustomer) && <Button variant="dark" onClick={addToCart}>{cartItemId ? 'Update cart item' : 'Add to cart'}</Button>}
          </div>
        </section>
      </div>

      <PromptDialog open={saveOpen} title="Save this design" description="Give it a name so you can find it later." fields={[{ name: 'name', label: 'Design name', required: true, placeholder: `${p.name}, navy` }]} submitLabel="Save design" onSubmit={async (v) => persist(v.name)} onCancel={() => setSaveOpen(false)} />
    </div>
  )
}
