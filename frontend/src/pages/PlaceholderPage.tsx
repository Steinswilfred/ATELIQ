import { EmptyState } from '../components/ui/States';

/** Honest stub: shows which phase delivers the page. Replaced as phases are implemented. */
export function PlaceholderPage({ title, phase }: { title: string; phase: number }) {
  return (
    <div className="mx-auto max-w-3xl px-4 py-12">
      <h1 className="mb-6 text-3xl">{title}</h1>
      <EmptyState title="Not built yet" hint={`This page is planned for Phase ${phase} of the roadmap.`} />
    </div>
  );
}
