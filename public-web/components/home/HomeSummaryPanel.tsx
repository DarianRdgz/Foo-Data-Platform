"use client";

import Link from "next/link";
import type { ComparableGeoLevel } from "@/lib/compare-validation";
import { MAX_COMPARE_IDS } from "@/lib/compare-validation";
import type { HomeTab } from "@/lib/home-query";
import type { HomeSummaryModel } from "@/lib/home-summary";

interface Props {
  tab: HomeTab;
  selectedStateFips: string | null;
  selectedCountyFips: string | null;
  selectedMetroCbsa: string | null;
  compareIds: string[];
  compareLevel?: ComparableGeoLevel;
  summary: HomeSummaryModel | null;
  loading: boolean;
  error: string | null;
  focusedAreaHref: string | null;
  onBackToUnitedStates: () => void;
  onBackToState: () => void;
  onClearCompareSelections: () => void;
  onAddToCompare?: (id: string, level: ComparableGeoLevel) => void;
}

export default function HomeSummaryPanel({
  tab,
  selectedStateFips,
  selectedCountyFips,
  selectedMetroCbsa,
  compareIds,
  compareLevel,
  summary,
  loading,
  error,
  focusedAreaHref,
  onBackToUnitedStates,
  onBackToState,
  onClearCompareSelections,
  onAddToCompare,
}: Props) {
  const isBrowseTab = tab === "browse";

  const showBackToState =
    isBrowseTab &&
    selectedStateFips !== null &&
    (selectedCountyFips !== null || selectedMetroCbsa !== null);

  const showBackToUnitedStates =
    isBrowseTab &&
    selectedStateFips !== null &&
    !showBackToState;

  const isShowingNational =
    isBrowseTab
      ? selectedStateFips === null
      : compareIds.length === 0;

  const focusedComparable =
    selectedMetroCbsa !== null
      ? { id: selectedMetroCbsa, level: "metro" as ComparableGeoLevel }
      : selectedCountyFips !== null
        ? { id: selectedCountyFips, level: "county" as ComparableGeoLevel }
        : selectedStateFips !== null
          ? { id: selectedStateFips, level: "state" as ComparableGeoLevel }
          : null;

  const alreadyInCompare =
    focusedComparable !== null &&
    compareIds.includes(focusedComparable.id);

  const atLimit = compareIds.length >= MAX_COMPARE_IDS;

  const showCompareCta =
    isBrowseTab &&
    focusedComparable !== null &&
    onAddToCompare !== undefined &&
    !atLimit &&
    (compareLevel === undefined || focusedComparable.level === compareLevel);

  return (
    <section className="summary-panel" aria-labelledby="summary-panel-title">
      <div className="summary-panel-header">
        <div>
          <p className="summary-kicker">Bottom summary panel</p>
          <h2 id="summary-panel-title">
            {isShowingNational ? "United States" : summary?.title ?? "Summary"}
          </h2>
          <p className="summary-subtitle">
            {isShowingNational
              ? "National defaults remain visible until a geography is selected."
              : summary?.subtitle ?? "Focused geography summary"}
          </p>
        </div>

        <div className="summary-actions">
          {focusedAreaHref ? (
            <Link href={focusedAreaHref} className="btn-secondary">
              Open full details
            </Link>
          ) : null}

          {showBackToState ? (
            <button
              type="button"
              className="btn-secondary"
              onClick={onBackToState}
            >
              Back to State
            </button>
          ) : null}

          {showBackToUnitedStates ? (
            <button
              type="button"
              className="btn-secondary"
              onClick={onBackToUnitedStates}
            >
              Back to U.S.
            </button>
          ) : null}

          {!isBrowseTab && compareIds.length > 0 ? (
            <button
              type="button"
              className="btn-secondary"
              onClick={onClearCompareSelections}
            >
              Clear compare
            </button>
          ) : null}
        </div>
      </div>

      {showCompareCta ? (
        <div className="summary-compare-row">
          <button
            type="button"
            className="summary-compare-cta"
            onClick={() =>
              onAddToCompare?.(focusedComparable.id, focusedComparable.level)
            }
            disabled={alreadyInCompare}
          >
            {alreadyInCompare
              ? "Added to compare ✓"
              : `Add ${focusedComparable.level} to compare`}
          </button>
        </div>
      ) : null}

      {loading ? (
        <div className="summary-status">Loading summary…</div>
      ) : error ? (
        <div className="summary-status summary-status-error">{error}</div>
      ) : summary ? (
        <div className="summary-grid">
          {summary.metrics.map((metric) => (
            <article key={metric.category} className="summary-card">
              <span className="summary-card-label">{metric.label}</span>
              <strong className="summary-card-value">{metric.valueText}</strong>
              <span className="summary-card-detail">
                {metric.detailText ??
                  (metric.available ? "" : "No snapshot available")}
              </span>
            </article>
          ))}
        </div>
      ) : (
        <div className="summary-status">No summary data available.</div>
      )}
    </section>
  );
}