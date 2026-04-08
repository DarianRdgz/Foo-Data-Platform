// public-web/app/compare/page.tsx
import type { Metadata } from "next";
import Link from "next/link";

export const metadata: Metadata = {
  title: "Compare Areas",
  description: "Compare public beta area routes.",
};

interface Props {
  searchParams: Promise<{ level?: string; ids?: string }>;
}

export default async function ComparePage({ searchParams }: Props) {
  const { level, ids } = await searchParams;

  const parsedIds = ids
    ? ids
        .split(",")
        .map((value) => value.trim())
        .filter(Boolean)
    : [];

  return (
    <div className="stub-page">
      <p className="stub-page-eyebrow">Compare</p>
      <h1>Compare Areas</h1>
      <p className="stub-desc">
        Full side-by-side comparison UI is deferred to Sprint 8. This scaffold
        page exists so the public compare route resolves safely now.
      </p>

      <div className="stub-meta">
        {level ? <span className="stub-badge">level: {level}</span> : null}
        {parsedIds.length > 0 ? (
          parsedIds.map((id) => (
            <span key={id} className="stub-badge">
              id: {id}
            </span>
          ))
        ) : (
          <span className="stub-badge">no params provided</span>
        )}
      </div>

      <div className="stub-links">
        <Link href="/browse" className="stub-link">
          Browse areas
        </Link>
        <Link href="/" className="stub-link">
          ← Home
        </Link>
      </div>
    </div>
  );
}