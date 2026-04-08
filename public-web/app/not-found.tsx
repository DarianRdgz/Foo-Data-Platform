// public-web/app/not-found.tsx
import Link from "next/link";

export default function NotFound() {
  return (
    <div className="not-found-page">
      <span className="not-found-code">404</span>
      <h1>We couldn&apos;t find that page</h1>
      <p>
        The page or area you requested does not exist, may have moved, or may
        use an invalid public identifier.
      </p>

      <div className="stub-links centered-links">
        <Link href="/" className="stub-link">
          Go home
        </Link>
        <Link href="/browse" className="stub-link">
          Browse areas
        </Link>
      </div>
    </div>
  );
}