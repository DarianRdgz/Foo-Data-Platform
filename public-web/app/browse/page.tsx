// public-web/app/browse/page.tsx
import type { Metadata } from "next";
import Link from "next/link";
import { US_STATES } from "@/lib/us-states";

export const metadata: Metadata = {
  title: "Browse States",
  description: "Browse public state entry pages for aboutmyarea.net.",
};

export default function BrowsePage() {
  return (
    <div className="stub-page">
      <p className="stub-page-eyebrow">Browse</p>
      <h1>All States</h1>
      <p className="stub-desc">
        Select a state to explore public beta routes and state-specific area
        navigation.
      </p>

      <div className="route-grid">
        {US_STATES.map(({ code, name }) => (
          <Link key={code} href={`/browse/${code}`} className="route-card">
            <span className="route-card-label">State FIPS {code}</span>
            <span className="route-card-title">{name}</span>
            <span className="route-card-path">/browse/{code}</span>
            <span className="route-card-desc">
              Crawlable state browse entry for {name}.
            </span>
          </Link>
        ))}
      </div>
    </div>
  );
}