// public-web/app/not-found.tsx
import Link from "next/link";

export default function NotFoundPage() {
  return (
    <main className="public-error-page">
      <p className="public-error-kicker">Page not found</p>
      <h1 className="public-error-title">We couldn’t find that route.</h1>
      <p className="public-error-message">
        Return to the homepage map or browse states to continue exploring.
      </p>
      <div className="public-error-actions">
        <Link href="/" className="btn-primary">Home</Link>
        <Link href="/browse" className="btn-secondary">Browse states</Link>
        <Link href="/compare" className="btn-secondary">Compare areas</Link>
      </div>
    </main>
  );
}