import type { Metadata } from "next";
import { buildPageMetadata } from "@/lib/metadata";
import Link from "next/link";
import { notFound } from "next/navigation";

import { getArea, getAreaChildren, PublicApiError } from "@/lib/api";
import {
  buildCanonicalStateHref,
  buildHomepageChildBrowseHref,
  buildHomepageStateHref,
  formatChildGeoLevel,
  isSupportedBrowseChildLevel,
  normalizeBrowseChildren,
} from "@/lib/browse";
import { isValidStateCode } from "@/lib/route-contract";
import { getStateByCode } from "@/lib/us-states";

interface Props {
  params: Promise<{ stateCode: string }>;
}

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { stateCode } = await params;
  if (!isValidStateCode(stateCode)) return { title: "Browse state" };

  const state = getStateByCode(stateCode);
  const stateName = state?.name ?? stateCode;

  return buildPageMetadata({
    title: `Browse ${stateName}`,
    description: `Browse counties and metro areas in ${stateName} on aboutmyarea.net.`,
    path: `/browse/${stateCode}`,
  });
}

export default async function BrowseStatePage({ params }: Props) {
  const { stateCode } = await params;

  if (!isValidStateCode(stateCode)) {
    notFound();
  }

  const [stateResult, childrenResult] = await Promise.allSettled([
    getArea("state", stateCode),
    getAreaChildren("state", stateCode),
  ]);

  if (stateResult.status === "rejected") {
    const err = stateResult.reason;

    if (err instanceof PublicApiError && err.isNotFound) {
      notFound();
    }

    throw err;
  }

  const childrenData =
    childrenResult.status === "fulfilled" ? childrenResult.value : null;

  const childrenFetchFailed =
    childrenResult.status === "rejected" &&
    !(
      childrenResult.reason instanceof PublicApiError &&
      childrenResult.reason.isNotFound
    );

  if (childrenFetchFailed) {
    throw childrenResult.reason;
  }

  const stateArea = stateResult.value;
  const childOptions = childrenData
    ? normalizeBrowseChildren(childrenData.children)
    : [];
  const childGeoLevel = childrenData?.childGeoLevel ?? null;
  const childLevelSupported = childGeoLevel
    ? isSupportedBrowseChildLevel(childGeoLevel)
    : false;

  return (
    <div className="stub-page">
      <p className="stub-page-eyebrow">Browse · State</p>
      <h1>{stateArea.displayLabel}</h1>
      <p className="stub-desc">
        Browse child areas for {stateArea.displayLabel}. For the full interactive
        experience, use the homepage map.
      </p>

      <div className="browse-actions">
        <Link href="/browse" className="browse-action-link-muted">
          ← All states
        </Link>
        <Link
          href={buildCanonicalStateHref(stateCode)}
          className="browse-action-link-muted"
        >
          State detail
        </Link>
        <Link
          href={buildHomepageStateHref(stateCode)}
          className="browse-action-link"
        >
          View on map
        </Link>
        {childGeoLevel ? (
          <Link
            href={buildHomepageChildBrowseHref(stateCode, childGeoLevel)}
            className="browse-action-link"
          >
            Browse {formatChildGeoLevel(childGeoLevel).toLowerCase()} on map
          </Link>
        ) : null}
      </div>

      <section className="browse-section">
        <h2 className="browse-section-title">
          {formatChildGeoLevel(childGeoLevel ?? "county")} in{" "}
          {stateArea.displayLabel}
        </h2>

        {!childGeoLevel || !childLevelSupported ? (
          <div className="browse-empty-state">
            <p>Child area data for this state is not yet available for beta browsing.</p>
          </div>
        ) : childOptions.length === 0 ? (
          <div className="browse-empty-state">
            <p>
              No {formatChildGeoLevel(childGeoLevel).toLowerCase()} data is
              available for this state yet.
            </p>
            <div className="stub-links">
              <Link href={buildCanonicalStateHref(stateCode)} className="stub-link">
                State detail page
              </Link>
              <Link href={buildHomepageStateHref(stateCode)} className="stub-link">
                View on map
              </Link>
              <Link href="/browse" className="stub-link">
                ← All states
              </Link>
            </div>
          </div>
        ) : (
          <div className="route-grid">
            {childOptions.map((opt) => (
              <Link
                key={opt.key}
                href={opt.href}
                className="route-card browse-child-card"
              >
                <span className="route-card-label">
                  {opt.geoLevelLabel} · {opt.identifier}
                </span>
                <span className="route-card-title">{opt.displayLabel}</span>
                <span className="route-card-path">{opt.href}</span>
                {opt.coverageCount > 0 ? (
                  <span className="route-card-desc">
                    {opt.coverageCount} data{" "}
                    {opt.coverageCount === 1 ? "category" : "categories"}
                  </span>
                ) : null}
              </Link>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}