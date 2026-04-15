import { Suspense } from "react";
import Link from "next/link";
import { ComparePageClient } from "../../components/compare/ComparePageClient";

interface ComparePageProps {
  searchParams: Promise<{
    level?: string;
    ids?: string;
  }>;
}

export const metadata = {
  title: "Compare Areas — aboutmyarea.net",
};

export default async function ComparePage({ searchParams }: ComparePageProps) {
  const resolvedSearchParams = await searchParams;

  const level = resolvedSearchParams.level ?? null;
  const ids = resolvedSearchParams.ids
    ? resolvedSearchParams.ids.split(",").map((v) => v.trim()).filter(Boolean)
    : [];

  return (
    <main className="compare-page-root">
      <div className="compare-page-header">
        <Link href="/" className="compare-page-back">
          ← Back to the map
        </Link>
        <h1 className="compare-page-title">Compare Areas</h1>
      </div>
      <Suspense fallback={<div className="compare-loading">Loading…</div>}>
        <ComparePageClient level={level} ids={ids} />
      </Suspense>
    </main>
  );
}