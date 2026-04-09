// public-web/components/home/HomeSummaryPanel.tsx
"use client";

import Link from "next/link";
import type { HomeTab } from "@/lib/home-query";
import type { HomeSummaryModel } from "@/lib/home-summary";

interface Props {
  tab: HomeTab;
  selectedStateFips: string | null;
  selectedCountyFips: string | null;
  selectedMetroCbsa: string | null;
  compareIds: string[];
  summary: HomeSummaryModel | null;
  loading: boolean;
  error: string | null;
  focusedAreaHref: string | null;
  onBackToUnitedStates: () => void;
  onBackToState: () => void;
  onClearCompareSelections: () => void;
}

export default function HomeSummaryPanel({
  tab,
  selectedStateFips,
  selectedCountyFips,
  selectedMetroCbsa,
  compareIds,
  summary,
  loading,
  error,
  focusedAreaHref,
  onBackToUnitedStates,
  onBackToState,
  onClearCompareSelections,
}: Props) {
  const isBrowseTab = tab === "browse";
  const showBackToState =
    isBrowseTab && selectedStateFips !== null &&
    (selectedCountyFips !== null || selectedMetroCbsa !== null);

  const showBackToUnitedStates =
    isBrowseTab &&
    selectedStateFips !== null &&
    !showBackToState;

  const isShowingNational =
    isBrowseTab
      ? selectedStateFips === null
      : compareIds.length === 0;

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