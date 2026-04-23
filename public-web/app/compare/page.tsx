import Link from "next/link";
import { ComparePageClient } from "@/components/compare/ComparePageClient";
import { parseDirectCompareParams } from "@/lib/compare-query";

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
  const params = await searchParams;

  const compareState = parseDirectCompareParams({
    level: params.level ?? null,
    ids: params.ids ?? null,
  });

  return (
    <main className="compare-page-root">
      <div className="compare-page-header">
        <Link href="/" className="compare-page-back">
          ← Back to the map
        </Link>
        <h1 className="compare-page-title">Compare Areas</h1>
      </div>

      <ComparePageClient
        initialLevel={compareState.level}
        initialIds={compareState.ids}
      />
    </main>
  );
}