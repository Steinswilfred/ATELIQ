import { Component, lazy, ReactNode, Suspense, useEffect, useMemo, useRef, useState } from 'react'
import { useReducedMotion } from 'framer-motion'
import { computeRenderState } from '../../lib/renderState'
import type { DesignConfig, MeasurementProfile, ProductT } from '../../lib/shopTypes'
import { Button } from '../ui/Button'
import { Skeleton } from '../ui/Skeleton'
import { Layered2D } from './Layered2D'
import type { ViewName } from './Garment3D'

const Garment3D = lazy(() => import('./Garment3D'))   // three.js is only downloaded when a 3D product is opened

function webglAvailable() {
  try { const c = document.createElement('canvas'); return !!(c.getContext('webgl2') || c.getContext('webgl')) } catch { return false }
}

class Boundary extends Component<{ children: ReactNode; onError: () => void }, { failed: boolean }> {
  state = { failed: false }
  static getDerivedStateFromError() { return { failed: true } }
  componentDidCatch() { this.props.onError() }
  render() { return this.state.failed ? null : this.props.children }
}

const VIEW_LABELS: [ViewName, string][] = [['front', 'Front'], ['back', 'Back'], ['left', 'Left'], ['right', 'Right']]

/** 3D when the product has a model and the browser supports it; otherwise photos with 2D layers. Gallery is always available. */
export function PreviewPanel({ product, config, measurementProfile }: { product: ProductT; config: DesignConfig; measurementProfile?: MeasurementProfile }) {
  const reduce = useReducedMotion() ?? false
  const rs = useMemo(() => computeRenderState(product, config, measurementProfile ? Object.fromEntries(Object.entries(measurementProfile.measurements).map(([key, value]) => [key, measurementProfile.unit === 'IN' ? value * 2.54 : value])) : undefined), [product, config, measurementProfile])
  const can3d = useMemo(() => !!product.modelUrl && webglAvailable(), [product.modelUrl])
  const [failed, setFailed] = useState(false)
  const has3d = can3d && !failed
  const [mode, setMode] = useState<'3d' | 'photos'>(has3d ? '3d' : 'photos')
  const [view, setView] = useState<ViewName>('front')
  const [nonce, setNonce] = useState(0)
  const [auto, setAuto] = useState(false)
  const [image, setImage] = useState(0)
  const api = useRef({ zoom: (_f: number) => undefined as void, cancel: () => undefined as void })
  useEffect(() => { setMode(has3d ? '3d' : 'photos'); setImage(0) }, [has3d, product.id])

  const show3d = has3d && mode === '3d'
  return (
    <div>
      <div className="relative aspect-[4/5] overflow-hidden rounded-2xl bg-surface">
        {show3d ? (
          <Boundary onError={() => setFailed(true)}>
            <Suspense fallback={<Skeleton className="h-full w-full rounded-none" />}>
              <Garment3D modelUrl={product.modelUrl!} rs={rs} view={view} viewNonce={nonce} autoRotate={auto} reduceMotion={reduce} api={api} label={`3D preview of ${product.name}. Drag to rotate, scroll or pinch to zoom, or use the view buttons.`} />
            </Suspense>
          </Boundary>
        ) : <Layered2D product={product} rs={rs} imageIndex={image} />}
        {show3d && (
          <div className="absolute right-3 top-3 flex flex-col gap-2">
            <Button type="button" variant="ghost" className="bg-canvas/90 px-3" aria-label="Zoom in" onClick={() => api.current.zoom(0.8)}>＋</Button>
            <Button type="button" variant="ghost" className="bg-canvas/90 px-3" aria-label="Zoom out" onClick={() => api.current.zoom(1.25)}>－</Button>
          </div>)}
      </div>

      {show3d && (
        <div className="mt-3 flex flex-wrap items-center gap-2" role="group" aria-label="Preview angle">
          {VIEW_LABELS.map(([v, label]) => <Button key={v} type="button" variant={view === v ? 'dark' : 'ghost'} aria-pressed={view === v} className="px-3 py-1.5" onClick={() => { setView(v); setNonce((n) => n + 1) }}>{label}</Button>)}
          {!reduce && <Button type="button" variant={auto ? 'dark' : 'ghost'} aria-pressed={auto} className="px-3 py-1.5" onClick={() => setAuto(!auto)}>360° spin</Button>}
        </div>)}

      <div className="mt-3 flex items-center gap-2">
        {has3d && <div className="flex gap-2" role="group" aria-label="Preview type">
          <Button type="button" variant={mode === '3d' ? 'dark' : 'ghost'} aria-pressed={mode === '3d'} className="px-3 py-1.5" onClick={() => setMode('3d')}>3D view</Button>
          <Button type="button" variant={mode === 'photos' ? 'dark' : 'ghost'} aria-pressed={mode === 'photos'} className="px-3 py-1.5" onClick={() => setMode('photos')}>Photos</Button></div>}
        {!has3d && product.modelUrl && <p className="text-xs">3D preview is not available on this device, so you are seeing photos.</p>}
        {product.modelUrl?.startsWith('builtin:') && has3d && mode === '3d' && <p className="text-xs">Simplified preview shape. Colours, fabric and options update live.</p>}
      </div>

      {!show3d && product.images.length > 1 && (
        <div className="mt-3 flex gap-2 overflow-x-auto">{product.images.map((im, i) => (
          <button key={im.url} type="button" onClick={() => setImage(i)} aria-label={`Show image ${i + 1}`} className={`h-16 w-16 shrink-0 overflow-hidden rounded-lg ring-2 ${i === image ? 'ring-primary' : 'ring-transparent'}`}><img src={im.url} alt="" className="h-full w-full object-cover" /></button>))}</div>)}
    </div>
  )
}
