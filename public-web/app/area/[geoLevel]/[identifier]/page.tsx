// public-web/app/area/[geoLevel]/[identifier]/page.tsx
import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import {
  isValidAreaDetailGeoLevel,
  isValidAreaIdentifier,
} from "@/lib/route-contract";

interface Props {
  params: Promise<{ geoLevel: string; identifier: string }>;
}

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { geoLevel, identifier } = await params;

  if (
    !isValidAreaDetailGeoLevel(geoLevel) ||
    !isValidAreaIdentifier(geoLevel, identifier)
  ) {
    return { title: "Area" };
  }

  return {
    title: `${geoLevel} ${identifier}`,
  };
}

export default async function AreaDetailPage({ params }: Props) {
  const { geoLevel, identifier } = await params;

  if (!isValidAreaDetailGeoLevel(geoLevel)) {
    notFound();
  }

  if (!isValidAreaIdentifier(geoLevel, identifier)) {
    notFound();
  }

  const showCountyParentStateLink = geoLevel === "county";
  const countyStateCode = showCountyParentStateLink ? identifier.slice(0, 2) : null;

  return (
    <div className="stub-page">
      <p className="stub-page-eyebrow">Area detail</p>
      <h1>
        {geoLevel} · {identifier}
      </h1>
      <p className="stub-desc">
        This is the canonical public area detail route for the beta. Real data
        sections will be wired in Sprint 8.
      </p>

      <div className="stub-meta">
        <span className="stub-badge">geoLevel: {geoLevel}</span>
        <span className="stub-badge">identifier: {identifier}</span>
        <span className="stub-badge">route: /area/[geoLevel]/[identifier]</span>
      </div>

      <div className="stub-links">
        {showCountyParentStateLink && countyStateCode ? (
          <Link href={`/area/state/${countyStateCode}`} className="stub-link">
            Parent state detail
          </Link>
        ) : null}
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