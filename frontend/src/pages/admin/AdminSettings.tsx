import { useEffect, useState } from 'react'
import { Button } from '../../components/ui/Button'
import { EmptyState } from '../../components/ui/EmptyState'
import { Skeleton } from '../../components/ui/Skeleton'
import { ApiError, get, http } from '../../lib/api'
import { applyTheme, ThemeColors, ThemeFonts } from '../../lib/theme'
import { useAsync } from '../../lib/useAsync'
import { useSite } from '../../store/siteStore'
import { toast } from '../../store/toastStore'

type S = Record<string, unknown>
const COLOR_LABELS: [keyof ThemeColors, string][] = [['primary', 'Primary (buttons, links)'], ['secondary', 'Secondary (headings, dark areas)'], ['background', 'Page background'], ['surface', 'Cards and panels'], ['accent', 'Soft accent'], ['text', 'Body text']]
const input = 'w-full rounded-lg border border-ink/20 bg-canvas px-3 py-2 text-sm focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/30'
const DEFAULT_COLORS: ThemeColors = { primary: '#E8505B', secondary: '#0A0A0A', background: '#FFFFFF', surface: '#F6F6F7', accent: '#F4A0A6', text: '#2B2B2E' }
const DEFAULT_FONTS: ThemeFonts = { heading: 'Bricolage Grotesque', body: 'Manrope', baseSize: 16 }

export default function AdminSettings() {
  const { data, loading, error } = useAsync(() => get<S>('/admin/settings'), [])
  const loadSite = useSite((s) => s.load)
  const [name, setName] = useState('')
  const [tagline, setTagline] = useState('')
  const [logoUrl, setLogoUrl] = useState('')
  const [faviconUrl, setFaviconUrl] = useState('')
  const [colors, setColors] = useState<ThemeColors>(DEFAULT_COLORS)
  const [fonts, setFonts] = useState<ThemeFonts>(DEFAULT_FONTS)
  const [tax, setTax] = useState('5'); const [delivery, setDelivery] = useState('99'); const [freeAbove, setFreeAbove] = useState('0'); const [cod, setCod] = useState(true)
  const [reqAvail, setReqAvail] = useState(true); const [respectCap, setRespectCap] = useState(true); const [escHours, setEscHours] = useState('24')
  const [commission, setCommission] = useState('15'); const [holdDays, setHoldDays] = useState('7'); const [lateFee, setLateFee] = useState('10'); const [autoRefund, setAutoRefund] = useState(true)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (!data) return
    setName((data['site.name'] as string) ?? ''); setTagline((data['site.tagline'] as string) ?? '')
    setLogoUrl((data['site.logoUrl'] as string) ?? ''); setFaviconUrl((data['site.faviconUrl'] as string) ?? '')
    setTax(String(data['checkout.taxRatePercent'] ?? 5)); setDelivery(String(data['checkout.deliveryCharge'] ?? 99)); setFreeAbove(String(data['checkout.freeDeliveryAbove'] ?? 0)); setCod(data['checkout.codEnabled'] !== false)
    setReqAvail(data['assignment.requireAvailable'] !== false); setRespectCap(data['assignment.respectCapacity'] !== false); setEscHours(String(data['assignment.escalationHours'] ?? 24))
    setCommission(String(data['finance.commissionPercent'] ?? 15)); setHoldDays(String(data['finance.settlementHoldDays'] ?? 7)); setLateFee(String(data['finance.lateCancellationFeePercent'] ?? 10)); setAutoRefund(data['finance.autoApproveCustomerRefunds'] !== false)
    setColors({ ...DEFAULT_COLORS, ...(data['theme.colors'] as Partial<ThemeColors>) }); setFonts({ ...DEFAULT_FONTS, ...(data['theme.fonts'] as Partial<ThemeFonts>) })
  }, [data])

  // live preview while editing; Save makes it permanent for everyone
  useEffect(() => { applyTheme(colors, fonts) }, [colors, fonts])

  const save = async () => {
    setSaving(true)
    try {
      await http.put('/admin/settings/site.name', JSON.stringify(name), { headers: { 'Content-Type': 'application/json' } })
      await http.put('/admin/settings/site.tagline', JSON.stringify(tagline), { headers: { 'Content-Type': 'application/json' } })
      await http.put('/admin/settings/site.logoUrl', JSON.stringify(logoUrl), { headers: { 'Content-Type': 'application/json' } })
      await http.put('/admin/settings/site.faviconUrl', JSON.stringify(faviconUrl), { headers: { 'Content-Type': 'application/json' } })
      const json = { headers: { 'Content-Type': 'application/json' } }
      await http.put('/admin/settings/checkout.taxRatePercent', JSON.stringify(Number(tax)), json)
      await http.put('/admin/settings/checkout.deliveryCharge', JSON.stringify(Number(delivery)), json)
      await http.put('/admin/settings/checkout.freeDeliveryAbove', JSON.stringify(Number(freeAbove)), json)
      await http.put('/admin/settings/checkout.codEnabled', JSON.stringify(cod), json)
      await http.put('/admin/settings/assignment.requireAvailable', JSON.stringify(reqAvail), json)
      await http.put('/admin/settings/assignment.respectCapacity', JSON.stringify(respectCap), json)
      await http.put('/admin/settings/assignment.escalationHours', JSON.stringify(Number(escHours)), json)
      await http.put('/admin/settings/finance.commissionPercent', JSON.stringify(Number(commission)), json)
      await http.put('/admin/settings/finance.settlementHoldDays', JSON.stringify(Number(holdDays)), json)
      await http.put('/admin/settings/finance.lateCancellationFeePercent', JSON.stringify(Number(lateFee)), json)
      await http.put('/admin/settings/finance.autoApproveCustomerRefunds', JSON.stringify(autoRefund), json)
      await http.put('/admin/settings/theme.colors', colors)
      await http.put('/admin/settings/theme.fonts', fonts)
      await loadSite()
      toast.success('Settings saved. The storefront now uses them.')
    } catch (e) { toast.error((e as ApiError).message) } finally { setSaving(false) }
  }

  if (error) return <EmptyState title="Could not load settings" hint={error.message} />
  if (loading) return <div className="space-y-3"><Skeleton className="h-10 w-1/3" /><Skeleton className="h-64" /></div>
  return (
    <div className="max-w-3xl space-y-6">
      <h1 className="text-3xl font-bold">Website settings</h1>
      <section className="space-y-4 rounded-xl bg-canvas p-5 ring-1 ring-ink/10">
        <h2 className="text-lg font-bold">Identity</h2>
        <label className="block text-sm font-semibold text-ink">Website name<input className={`${input} mt-1 font-normal`} value={name} onChange={(e) => setName(e.target.value)} /></label>
        <label className="block text-sm font-semibold text-ink">Tagline<input className={`${input} mt-1 font-normal`} value={tagline} onChange={(e) => setTagline(e.target.value)} /></label>
        <label className="block text-sm font-semibold text-ink">Logo address<input className={`${input} mt-1 font-normal`} placeholder="https://… or /logo.svg" value={logoUrl} onChange={(e) => setLogoUrl(e.target.value)} /></label>
        <label className="block text-sm font-semibold text-ink">Favicon address<input className={`${input} mt-1 font-normal`} placeholder="https://… or /favicon.ico" value={faviconUrl} onChange={(e) => setFaviconUrl(e.target.value)} /></label>
        <p className="text-xs">Image upload arrives with file storage; for now paste an address.</p>
      </section>
      <section className="rounded-xl bg-canvas p-5 ring-1 ring-ink/10">
        <h2 className="text-lg font-bold">Colours</h2><p className="text-xs">Changes preview on this page straight away and go live for everyone when you save.</p>
        <div className="mt-4 grid gap-4 sm:grid-cols-2">{COLOR_LABELS.map(([k, label]) => (
          <label key={k} className="flex items-center gap-3 text-sm font-semibold text-ink"><input type="color" aria-label={label} value={colors[k]} onChange={(e) => setColors({ ...colors, [k]: e.target.value.toUpperCase() })} className="h-10 w-12 rounded border border-ink/20" />
            <span>{label}<br /><span className="font-normal text-xs">{colors[k]}</span></span></label>))}</div>
      </section>
      <section className="space-y-4 rounded-xl bg-canvas p-5 ring-1 ring-ink/10">
        <h2 className="text-lg font-bold">Fonts</h2>
        <p className="text-xs">Use any Google Fonts family name.</p>
        <div className="grid gap-4 sm:grid-cols-3">
          <label className="text-sm font-semibold text-ink">Headings<input className={`${input} mt-1 font-normal`} value={fonts.heading} onChange={(e) => setFonts({ ...fonts, heading: e.target.value })} /></label>
          <label className="text-sm font-semibold text-ink">Body text<input className={`${input} mt-1 font-normal`} value={fonts.body} onChange={(e) => setFonts({ ...fonts, body: e.target.value })} /></label>
          <label className="text-sm font-semibold text-ink">Base size (px)<input type="number" min={12} max={22} className={`${input} mt-1 font-normal`} value={fonts.baseSize} onChange={(e) => setFonts({ ...fonts, baseSize: Number(e.target.value) })} /></label>
        </div>
      </section>
      <section className="space-y-4 rounded-xl bg-canvas p-5 ring-1 ring-ink/10"><h2 className="text-lg font-bold">Checkout charges</h2>
        <p className="text-xs">Applied to every new order. Confirm the correct GST rate and delivery policy for your business; the starting values are examples. Delivery is not taxed in this model.</p>
        <div className="grid gap-4 sm:grid-cols-3">
          <label className="text-sm font-semibold text-ink">Tax rate (%)<input type="number" min={0} max={100} step="0.01" className={`${input} mt-1 font-normal`} value={tax} onChange={(e) => setTax(e.target.value)} /></label>
          <label className="text-sm font-semibold text-ink">Delivery charge (₹)<input type="number" min={0} step="0.01" className={`${input} mt-1 font-normal`} value={delivery} onChange={(e) => setDelivery(e.target.value)} /></label>
          <label className="text-sm font-semibold text-ink">Free delivery above (₹)<input type="number" min={0} step="0.01" className={`${input} mt-1 font-normal`} value={freeAbove} onChange={(e) => setFreeAbove(e.target.value)} /><span className="text-xs font-normal">0 = never free</span></label></div>
        <label className="flex items-center gap-2 text-sm font-semibold text-ink"><input type="checkbox" className="accent-primary" checked={cod} onChange={(e) => setCod(e.target.checked)} />Offer cash on delivery</label></section>
      <section className="space-y-4 rounded-xl bg-canvas p-5 ring-1 ring-ink/10"><h2 className="text-lg font-bold">Commission, payouts and cancellations</h2>
        <p className="text-xs">The starting values are examples. Changes apply to new orders; earnings already created keep the rate they were created with.</p>
        <div className="grid gap-4 sm:grid-cols-3">
          <label className="text-sm font-semibold text-ink">Commission on stitching (%)<input type="number" min={0} max={90} step="0.01" className={`${input} mt-1 font-normal`} value={commission} onChange={(e) => setCommission(e.target.value)} /></label>
          <label className="text-sm font-semibold text-ink">Days before a tailor’s earning is payable<input type="number" min={0} max={90} className={`${input} mt-1 font-normal`} value={holdDays} onChange={(e) => setHoldDays(e.target.value)} /></label>
          <label className="text-sm font-semibold text-ink">Fee for cancelling after a tailor is assigned (%)<input type="number" min={0} max={100} step="0.01" className={`${input} mt-1 font-normal`} value={lateFee} onChange={(e) => setLateFee(e.target.value)} /></label></div>
        <label className="flex items-center gap-2 text-sm font-semibold text-ink"><input type="checkbox" className="accent-primary" checked={autoRefund} onChange={(e) => setAutoRefund(e.target.checked)} />Refund customer cancellations automatically (no administrator approval)</label></section>
      <section className="space-y-4 rounded-xl bg-canvas p-5 ring-1 ring-ink/10"><h2 className="text-lg font-bold">Order assignment rules</h2>
        <p className="text-xs">Who is offered a new order. Tailors must always be approved and in the order’s category. Administrators can override these rules for a single order.</p>
        <label className="flex items-center gap-2 text-sm font-semibold text-ink"><input type="checkbox" className="accent-primary" checked={reqAvail} onChange={(e) => setReqAvail(e.target.checked)} />Only tailors whose status is Available</label>
        <label className="flex items-center gap-2 text-sm font-semibold text-ink"><input type="checkbox" className="accent-primary" checked={respectCap} onChange={(e) => setRespectCap(e.target.checked)} />Only tailors below their active-order limit</label>
        <label className="block text-sm font-semibold text-ink">Alert admins if nobody accepts within (hours)<input type="number" min={0} max={720} className={`${input} mt-1 w-40 font-normal`} value={escHours} onChange={(e) => setEscHours(e.target.value)} /><span className="text-xs font-normal">0 turns the alert off</span></label></section>
      <div className="flex gap-3"><Button loading={saving} onClick={save}>Save settings</Button><Button variant="ghost" onClick={() => { setColors(DEFAULT_COLORS); setFonts(DEFAULT_FONTS) }}>Reset theme to default</Button></div>
      <p className="text-xs">Header, footer and homepage banner editors are planned once the storefront pages exist (Phase 5).</p>
    </div>
  )
}
