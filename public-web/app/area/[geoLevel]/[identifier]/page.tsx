// public-web/app/area/[geoLevel]/[identifier]/page.tsx
import type { Metadata } from "next";
import { buildPageMetadata } from "@/lib/metadata";
import { notFound } from "next/navigation";
import {
  getArea,
  getAreaHistory,
  PublicApiError,
  type AreaDetailGeoLevel,
} from "@/lib/api";
import {
  isValidAreaDetailGeoLevel,
  isValidAreaIdentifier,
} from "@/lib/route-contract";
import { buildAreaDetailModel } from "@/lib/area-detail";
import { AreaDetailSections } from "@/components/area/AreaDetailSections";

interface Props {
  params: Promise<{ geoLevel: string; identifier: string }>;
}

function isSupportedDetailLevel(
  geoLevel: string
): geoLevel is Exclude<AreaDetailGeoLevel, "zip"> {
  return geoLevel === "state" || geoLevel === "county" || geoLevel === "metro";
}

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { geoLevel, identifier } = await params;

  if (!isValidAreaDetailGeoLevel(geoLevel) || !isValidAreaIdentifier(geoLevel, identifier)) {
    return { title: "Area" };
  }

  try {
    const area = await getArea(geoLevel as AreaDetailGeoLevel, identifier);
    const levelLabel = geoLevel.charAt(0).toUpperCase() + geoLevel.slice(1);

    return buildPageMetadata({
      title: area.displayLabel,
      description: `${levelLabel} data for ${area.displayLabel} — housing, economic, and risk indicators on aboutmyarea.net.`,
      path: `/area/${geoLevel}/${identifier}`,
    });
  } catch {
    return { title: `${geoLevel} ${identifier}` };
  }
}

export default async function AreaDetailPage({ params }: Props) {
  const { geoLevel, identifier } = await params;

  if (!isValidAreaDetailGeoLevel(geoLevel)) {
    notFound();
  }

  if (!isValidAreaIdentifier(geoLevel, identifier)) {
    notFound();
  }

  if (!isSupportedDetailLevel(geoLevel)) {
    notFound();
  }

  const [areaResult, historyResult] = await Promise.allSettled([
    getArea(geoLevel, identifier),
    getAreaHistory(geoLevel, identifier, "housing.home_value", 12),
  ]);

  if (areaResult.status === "rejected") {
    const error = areaResult.reason;

    if (error instanceof PublicApiError && error.isNotFound) {
      notFound();
    }

    throw error;
  }

  const history =
    historyResult.status === "fulfilled" ? historyResult.value : null;

  const model = buildAreaDetailModel(areaResult.value, history);

  return (
    <div className="container">
      <div className="detail-page">
        <AreaDetailSections model={model} />
      </div>
    </div>
  );
}