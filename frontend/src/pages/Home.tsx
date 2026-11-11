import { motion, useReducedMotion } from 'framer-motion'
import { Link } from 'react-router-dom'
import { GridSkeleton, ProductGrid } from '../components/shop/ProductCard'
import { Button } from '../components/ui/Button'
import { get } from '../lib/api'
import type { CategoryNode, CollectionT, ProductCardT } from '../lib/shopTypes'
import { useAsync } from '../lib/useAsync'

interface Home { sections: CategoryNode[]; featured: ProductCardT[]; trending: ProductCardT[]; collections: CollectionT[] }

/** Tape-measure strip: the one orchestrated motion on the page, drawn once on load. */
function TapeMeasure() {
  const reduce = useReducedMotion()
  const ticks = Array.from({ length: 61 }, (_, i) => i)
  return (
    <motion.div aria-hidden className="relative h-14 w-full origin-left overflow-hidden rounded-md bg-primary" initial={reduce ? false : { scaleX: 0 }} animate={{ scaleX: 1 }} transition={{ duration: 1.1, ease: [0.22, 1, 0.36, 1] }}>
      <div className="absolute inset-x-0 bottom-0 flex h-full items-end justify-between px-3">
        {ticks.map((i) => <span key={i} className="w-px bg-white/90" style={{ height: i % 10 === 0 ? '70%' : i % 5 === 0 ? '48%' : '28%' }} />)}
      </div>
    </motion.div>
  )
}

const Section = ({ title, to, children }: { title: string; to?: string; children: React.ReactNode }) => (
  <section className="mx-auto max-w-7xl px-4 py-10">
    <div className="mb-6 flex items-end justify-between"><h2 className="text-2xl font-bold md:text-3xl">{title}</h2>{to && <Link to={to} className="text-sm font-semibold text-primary">See all →</Link>}</div>{children}
  </section>
)

export default function Home() {
  const { data, loading } = useAsync(() => get<Home>('/public/home'), [])
  return (
    <>
      <section className="mx-auto max-w-7xl px-4 py-16 md:py-24">
        <h1 className="max-w-3xl text-5xl font-bold leading-[1.05] md:text-7xl">Clothing cut to your measurements.</h1>
        <p className="mt-6 max-w-xl text-lg">Design every detail of a shirt, trouser or suit, then choose the tailor whose craft and price suit you. You approve the fabric before a single cut is made.</p>
        <div className="mt-8 flex flex-wrap gap-3"><Link to="/shop"><Button>Start designing</Button></Link><Link to="/trending"><Button variant="ghost">See trending collections</Button></Link></div>
        <div className="mt-14"><TapeMeasure /></div>
      </section>

      {data && data.sections.length > 0 && (
        <section className="mx-auto grid max-w-7xl gap-4 px-4 py-6 sm:grid-cols-3">
          {data.sections.map((s) => (
            <Link key={s.id} to={`/${s.slug}`} className="group relative block aspect-[3/2] overflow-hidden rounded-2xl bg-ink">
              {s.imageUrl && <img src={s.imageUrl} alt="" className="absolute inset-0 h-full w-full object-cover opacity-70 transition duration-300 group-hover:scale-105" />}
              <div className="absolute inset-x-0 bottom-0 bg-gradient-to-t from-ink/80 to-transparent p-5 text-white"><div className="font-heading text-2xl font-bold">{s.name}</div><div className="text-xs opacity-80">{s.productCount} style{s.productCount === 1 ? '' : 's'}</div></div>
            </Link>))}
        </section>)}

      {loading && <Section title="Featured"><GridSkeleton n={4} /></Section>}
      {data && data.featured.length > 0 && <Section title="Featured" to="/shop?featured=true"><ProductGrid items={data.featured} /></Section>}
      {data && data.trending.length > 0 && <Section title="Trending now" to="/trending"><ProductGrid items={data.trending} /></Section>}
      {data && data.collections.slice(0, 2).map((c) => <Section key={c.id} title={c.name} to="/trending"><ProductGrid items={c.products.slice(0, 4)} /></Section>)}
      {data && data.featured.length === 0 && data.trending.length === 0 && data.collections.length === 0 && (
        <Section title="The collection is being prepared"><p className="text-sm">New styles will appear here soon.</p></Section>)}
    </>
  )
}
