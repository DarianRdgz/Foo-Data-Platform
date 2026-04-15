// public-web/components/compare/ComparePanel.tsx
"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import type { ComparableGeoLevel } from "@/lib/compare-validation";
import {
  validateCompareRequest,
  getCompareLevelMessage,
  MAX_COMPARE_IDS,
} from "@/lib/compare-validation";
import {
  getAreasForComparison,
  type CompareAreaResult,
  type AreaResponse,
} from "@/lib/api";

interface ComparePanelProps {
  level: string | null | undefined;
  ids: string[];
  onRemoveId?: (id: string) => void;
  standalone?: boolean;
}

interface ResolvedCompareResults {
  requestKey: string;
  data: CompareAreaResult[];
}

function getAreaDisplayName(area: AreaResponse): string {
  return area.displayLabel ?? area.name ?? "Unknown area";
}

function getAreaDetailHref(level: ComparableGeoLevel, id: string): string {
  return `/area/${level}/${id}`;
}

function getAreaMetricRows(area: AreaResponse): Array<{
  key: string;
  label: string;
  value: string;
}> {
  return area.snapshots.slice(0, 4).map((snapshot) => {
    const payloadValue =
      typeof snapshot.payload?.value === "number"
        ? snapshot.payload.value.toLocaleString()
        : typeof snapshot.payload?.value === "string"
        ? snapshot.payload.value
        : "Not available yet";

    return {
      key: snapshot.category,
      label: snapshot.category,
      value: payloadValue,
    };
  });
}

export function ComparePanel({
  level,
  ids,
  onRemoveId,
  standalone = false,
}: ComparePanelProps) {
  const validation = validateCompareRequest(level, ids);
  const resolvedLevel = level as ComparableGeoLevel | undefined;

  const requestKey = useMemo(() => {
    if (!validation.valid || !resolvedLevel) {
      return null;
    }

    return `${resolvedLevel}:${ids.join(",")}`;
  }, [validation.valid, resolvedLevel, ids]);

  const [resolvedResults, setResolvedResults] =
    useState<ResolvedCompareResults | null>(null);

  useEffect(() => {
    if (!requestKey || !resolvedLevel) {
      return;
    }

    let cancelled = false;

    getAreasForComparison(resolvedLevel, ids).then((data) => {
      if (!cancelled) {
        setResolvedResults({
          requestKey,
          data,
        });
      }
    });

    return () => {
      cancelled = true;
    };
  }, [requestKey, resolvedLevel, ids]);

  const visibleResults =
    requestKey && resolvedResults?.requestKey === requestKey
      ? resolvedResults.data
      : null;

  const loading = Boolean(requestKey && visibleResults === null);

  return (
    <div className="compare-panel">
      {ids.length > 0 && (
        <div className="compare-chip-strip">
          {ids.map((id) => (
            <span key={id} className="compare-chip">
              <span className="compare-chip-label">{id}</span>
              {onRemoveId && (
                <button
                  type="button"
                  className="compare-chip-remove"
                  onClick={() => onRemoveId(id)}
                  aria-label={`Remove ${id} from comparison`}
                >
                  ×
                </button>
              )}
            </span>
          ))}

          {ids.length < MAX_COMPARE_IDS && resolvedLevel && (
            <span className="compare-chip compare-chip-hint">
              {getCompareLevelMessage(resolvedLevel)}
            </span>
          )}
        </div>
      )}

      {!validation.valid && validation.message && (
        <div className="compare-validation-banner" role="status">
          <span>{validation.message}</span>

          {standalone && (
            <Link href="/" className="compare-return-link">
              ← Return to the map
            </Link>
          )}
        </div>
      )}

      {ids.length === 0 && resolvedLevel && (
        <div className="compare-empty-prompt">
          {getCompareLevelMessage(resolvedLevel)}
        </div>
      )}

      {loading && (
        <div className="compare-loading" aria-live="polite">
          Loading comparison…
        </div>
      )}

      {!loading && validation.valid && visibleResults && (
        <div className="compare-cards-grid">
          {visibleResults.map((result) => {
            if (result.status === "rejected" || !result.area) {
              return (
                <div key={result.id} className="compare-card compare-card--error">
                  <p className="compare-card-error-message">
                    Could not load area {result.id}.
                  </p>
                  <p className="compare-card-error-detail">
                    {result.error ?? "Unknown error."}
                  </p>
                </div>
              );
            }

            const area = result.area;
            const metricRows = getAreaMetricRows(area);

            return (
              <div key={result.id} className="compare-card">
                <div className="compare-card-header">
                  <h3 className="compare-card-title">{getAreaDisplayName(area)}</h3>

                  {onRemoveId && (
                    <button
                      type="button"
                      className="compare-card-remove"
                      onClick={() => onRemoveId(result.id)}
                      aria-label={`Remove ${getAreaDisplayName(area)} from comparison`}
                    >
                      ×
                    </button>
                  )}
                </div>

                <p className="compare-card-subtitle">
                  {resolvedLevel} · {result.id}
                </p>

                {metricRows.length > 0 ? (
                  metricRows.map((row) => (
                    <div key={row.key} className="compare-card-metric">
                      <span className="compare-card-metric-label">{row.label}</span>
                      <span className="compare-card-metric-value">{row.value}</span>
                    </div>
                  ))
                ) : (
                  <p className="compare-card-empty">No summary metrics available yet.</p>
                )}

                {resolvedLevel && (
                  <Link
                    href={getAreaDetailHref(resolvedLevel, result.id)}
                    className="compare-card-detail-link"
                  >
                    Open full details →
                  </Link>
                )}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}