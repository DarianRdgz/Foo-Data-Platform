"use client";

import { useEffect } from "react";
import Link from "next/link";

export default function GlobalError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    console.error(error);
  }, [error]);

  return (
    <main className="public-error-page">
      <p className="public-error-kicker">Something went wrong</p>
      <h1 className="public-error-title">We couldn’t load this page.</h1>
      <p className="public-error-message">
        Try refreshing, return to the homepage, or continue browsing public beta areas.
      </p>
      <div className="public-error-actions">
        <button type="button" className="btn-primary" onClick={() => reset()}>
          Try again
        </button>
        <Link href="/" className="btn-secondary">Home</Link>
        <Link href="/browse" className="btn-secondary">Browse states</Link>
      </div>
    </main>
  );
}