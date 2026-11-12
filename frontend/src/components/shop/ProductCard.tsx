import { motion } from 'framer-motion'
import { Link } from 'react-router-dom'
import { money } from '../../lib/format'
import { getMediaUrl } from '../../lib/api'
import type { ProductCardT } from '../../lib/shopTypes'

export function ProductCard({ p }: { p: ProductCardT }) {
  const imageUrl = getMediaUrl(p.imageUrl)

  return (
    <motion.article
      whileHover={{ y: -4 }}
      transition={{ duration: 0.2 }}
      className="group"
    >
      <Link to={`/product/${p.slug}`} className="block">
        <div className="aspect-[4/5] overflow-hidden rounded-xl bg-surface">
          {imageUrl ? (
            <img
              src={imageUrl}
              alt={p.name}
              loading="lazy"
              className="h-full w-full object-cover transition duration-300 group-hover:scale-105"
              onError={(e) => {
                console.error('Product image failed to load:', imageUrl)
                e.currentTarget.style.display = 'none'
              }}
            />
          ) : (
            <div
              className="grid h-full place-items-center font-heading text-5xl text-ink/20"
              aria-hidden
            >
              {p.name.charAt(0)}
            </div>
          )}
        </div>

        <div className="mt-3 flex items-start justify-between gap-2">
          <div>
            <h3 className="font-sans text-sm font-semibold text-ink">
              {p.name}
            </h3>
            <p className="text-xs">{p.categoryName}</p>
          </div>

          <div className="text-right text-sm font-semibold text-ink">
            from {money(p.basePrice)}

            {p.trending && (
              <div className="text-[10px] font-bold uppercase text-primary">
                Trending
              </div>
            )}
          </div>
        </div>
      </Link>
    </motion.article>
  )
}

export function ProductGrid({ items }: { items: ProductCardT[] }) {
  return (
    <div className="grid grid-cols-2 gap-x-4 gap-y-8 md:grid-cols-3 lg:grid-cols-4">
      {items.map((p) => (
        <ProductCard key={p.id} p={p} />
      ))}
    </div>
  )
}

export function GridSkeleton({ n = 8 }: { n?: number }) {
  return (
    <div
      className="grid grid-cols-2 gap-x-4 gap-y-8 md:grid-cols-3 lg:grid-cols-4"
      aria-hidden
    >
      {Array.from({ length: n }, (_, i) => (
        <div key={i}>
          <div className="aspect-[4/5] animate-pulse rounded-xl bg-ink/10" />
          <div className="mt-3 h-4 w-2/3 animate-pulse rounded bg-ink/10" />
        </div>
      ))}
    </div>
  )
}
