import type { RenderState } from '../../lib/renderState'
import type { ProductT } from '../../lib/shopTypes'

/** Fallback when a product has no 3D model or WebGL is unavailable: the product photo with 2D option layers stacked on top. */
export function Layered2D({ product, rs, imageIndex }: { product: ProductT; rs: RenderState; imageIndex: number }) {
  const base = product.images[imageIndex] ?? product.images[0]
  return (
    <div className="relative h-full w-full bg-surface">
      {base ? <img src={base.url} alt={base.alt ?? product.name} className="h-full w-full object-cover" />
        : <div className="grid h-full place-items-center font-heading text-8xl text-ink/20" aria-hidden>{product.name.charAt(0)}</div>}
      {imageIndex === 0 && rs.layers.map((l) => <img key={l.name} src={l.url} alt="" className="absolute inset-0 h-full w-full object-contain" />)}
      {rs.fabricColor && <span className="absolute bottom-3 left-3 flex items-center gap-2 rounded-full bg-canvas/90 px-3 py-1 text-xs font-semibold text-ink shadow"><span className="h-4 w-4 rounded-full ring-1 ring-ink/20" style={{ background: rs.fabricColor }} />Chosen colour</span>}
    </div>
  )
}
