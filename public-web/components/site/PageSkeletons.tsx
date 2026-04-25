export function CardGridSkeleton({ count = 6 }: { count?: number }) {
  return (
    <div className="route-grid">
      {Array.from({ length: count }).map((_, i) => (
        <div key={i} className="skeleton-card">
          <div className="skeleton-shimmer skeleton-card-title" />
          <div className="skeleton-shimmer skeleton-card-body" />
          <div className="skeleton-shimmer skeleton-card-body" />
        </div>
      ))}
    </div>
  );
}

export function DetailPageSkeleton() {
  return (
    <div className="area-loading-shell">
      <div className="skeleton-shimmer skeleton-line" style={{ width: "12rem", height: "1.5rem" }} />
      <div className="skeleton-shimmer skeleton-line" style={{ width: "30%" }} />
      <div className="skeleton-card">
        <div className="skeleton-shimmer skeleton-card-title" />
        <div className="skeleton-shimmer skeleton-card-body" />
        <div className="skeleton-shimmer skeleton-card-body" />
      </div>
      <div className="skeleton-card">
        <div className="skeleton-shimmer skeleton-card-title" />
        <div className="skeleton-shimmer skeleton-card-body" />
        <div className="skeleton-shimmer skeleton-card-body" />
      </div>
    </div>
  );
}

export function CompareCardsSkeleton({ count = 2 }: { count?: number }) {
  return (
    <div className="compare-cards-grid">
      {Array.from({ length: count }).map((_, i) => (
        <div key={i} className="skeleton-card">
          <div className="skeleton-shimmer skeleton-card-title" />
          <div className="skeleton-shimmer skeleton-card-body" />
          <div className="skeleton-shimmer skeleton-card-body" />
          <div className="skeleton-shimmer skeleton-card-body" />
        </div>
      ))}
    </div>
  );
}

export function MapSurfaceSkeleton() {
  return <div className="skeleton-shimmer skeleton-map" />;
}

export function SummaryPanelSkeleton() {
  return (
    <div className="skeleton-card">
      <div className="skeleton-shimmer skeleton-card-title" />
      <div className="skeleton-shimmer skeleton-card-body" />
      <div className="skeleton-shimmer skeleton-card-body" />
      <div className="skeleton-shimmer skeleton-card-body" />
    </div>
  );
}