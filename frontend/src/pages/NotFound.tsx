import { Link } from 'react-router-dom';

export function NotFound() {
  return (
    <div className="mx-auto max-w-xl px-4 py-24 text-center">
      <h1 className="text-4xl">Page not found</h1>
      <p className="mt-3">The page you are looking for does not exist or has moved.</p>
      <Link to="/" className="mt-6 inline-block rounded-full bg-ink px-5 py-2.5 text-sm font-semibold text-white">Back to home</Link>
    </div>
  );
}
