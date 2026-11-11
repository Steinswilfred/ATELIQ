import { useLocation } from 'react-router-dom'
import { EmptyState } from '../components/ui/EmptyState'

/** Marks routes whose implementation is scheduled in DEVELOPMENT_ROADMAP.md. */
export default function Placeholder() {
  const { pathname } = useLocation()
  return (
    <div className="mx-auto max-w-3xl px-4 py-16">
      <EmptyState title="This page is not built yet" hint={`${pathname} is planned for a later phase. See docs/DEVELOPMENT_ROADMAP.md.`} />
    </div>
  )
}
