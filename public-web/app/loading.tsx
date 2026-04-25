import { MapSurfaceSkeleton, SummaryPanelSkeleton } from "@/components/site/PageSkeletons";

export default function RootLoading() {
  return (
    <div className="home-loading-shell">
      <MapSurfaceSkeleton />
      <SummaryPanelSkeleton />
    </div>
  );
}