// public-web/app/error.tsx
"use client";

import Link from "next/link";
import { useEffect } from "react";

interface Props {
  error: Error & { digest?: string };
  reset: () => void;
}

export default function ErrorPage({ error, reset }: Props) {
  useEffect(() => {
    console.error("[public-web error boundary]", error);
  }, [error]);

  return (
    <div className="error-page">
      <div className="error-icon">⚠️</div>
      <h1>Something went wrong</h1>
      <p>
        We ran into an unexpected problem while loading this page. You can try
        again or return to the home page.
      </p>

      <div className="error-actions">
        <button type="button" className="btn-primary" onClick={reset}>
          Try again
        </button>
        <Link href="/" className="btn-secondary">
          Go home
        </Link>
      </div>
    </div>
  );
}