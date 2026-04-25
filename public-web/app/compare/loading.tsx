import { CompareCardsSkeleton } from "@/components/site/PageSkeletons";

export default function CompareLoading() {
  return (
    <main className="compare-page-root">
      <div className="compare-page-header">
        <div className="skeleton-shimmer skeleton-line" style={{ width: "6rem" }} />
        <div className="skeleton-shimmer skeleton-line" style={{ width: "10rem", height: "1.25rem" }} />
      </div>
      <CompareCardsSkeleton count={2} />
    </main>
  );
}