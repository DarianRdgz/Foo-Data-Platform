import { CardGridSkeleton } from "@/components/site/PageSkeletons";

export default function BrowseLoading() {
  return (
    <div className="stub-page">
      <div className="skeleton-shimmer skeleton-line" style={{ width: "8rem", marginBottom: "1rem" }} />
      <div className="skeleton-shimmer skeleton-line" style={{ width: "16rem", height: "1.5rem", marginBottom: "1.5rem" }} />
      <CardGridSkeleton count={12} />
    </div>
  );
}