// public-web/app/browse/page.tsx
import type { Metadata } from "next";
import { buildPageMetadata } from "@/lib/metadata";
import Link from "next/link";
import { US_STATES } from "@/lib/us-states";
import {
  buildBrowseStateHref,
  buildCanonicalStateHref,
  buildHomepageStateHref,
} from "@/lib/browse";

export const metadata: Metadata = buildPageMetadata({
  title: "Browse States",
  description:
    "Browse states to explore county and metro area data on aboutmyarea.net.",
  path: "/browse",
});

export default function BrowsePage() {
  return (
    <div className="stub-page">
      <p className="stub-page-eyebrow">Browse</p>
      <h1>All States</h1>
      <p className="stub-desc">
        Browse public beta areas by state. For the full interactive experience, use the{" "}
        <Link href="/" className="detail-link">homepage map</Link>.
      </p>

      <div className="route-grid">
        {US_STATES.map(({ code, name }) => (
          <div key={code} className="browse-state-card">
            <span className="route-card-label">State FIPS {code}</span>
            <span className="route-card-title">{name}</span>
            <div className="browse-card-links">
              <Link href={buildBrowseStateHref(code)} className="browse-card-link-primary">
                Browse {name} areas →
              </Link>
              <Link href={buildCanonicalStateHref(code)} className="browse-card-link-secondary">
                State detail
              </Link>
              <Link href={buildHomepageStateHref(code)} className="browse-card-link-secondary">
                View on map
              </Link>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}