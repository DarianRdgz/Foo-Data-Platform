"use client";

import { useEffect, useState } from "react";
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

function getAreaDisplayName(area: AreaResponse, fallbackId: string): string {
  const candidate =
    (area as { name?: string }).name ??
    (area as { displayName?: string }).displayName ??
    (area as { title?: string }).title;

  return candidate && candidate.trim().length > 0 ? candidate : fallbackId;
}

function getAreaDetailHref(level: ComparableGeoLevel, id: string): string {
  return `/area/${level}/${id}`;
}

export function ComparePanel({
  level,
  ids,
  onRemoveId,
  standalone = false,
}: ComparePanelProps) {
  const [results, setResults] = useState<CompareAreaResult[] | null>(null);
  const [loading, setLoading] = useState(false);

  const validation = validateCompareRequest(level, ids);
  const resolvedLevel =
    level && (level === "state" || level === "county" || level === "metro")
      ? level
      : null;

  useEffect(() => {
    if (!validation.valid || !resolvedLevel) {
      setResults(null);
      setLoading(false);
      return;
    }

    let cancelled = false;
    setLoading(true);

    getAreasForComparison(resolvedLevel, ids)
      .then((data) => {
        if (!cancelled) {
          setResults(data);
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [resolvedLevel, ids, validation.valid]);

  return (
    <div className="compare-panel">
      {ids.length > 0 && (
        <div className="compare-chip-strip">
          {ids.map((id) => (
            <span key={id} className="compare-chip">
              <span className="compare-chip-label">{id}</span>
              {onRemoveId ? (
                <button
                  type="button"
                  className="compare-chip-remove"
                  onClick={() => onRemoveId(id)}
                  aria-label={`Remove ${id} from comparison`}
                >
                  ×
                </button>
              ) : null}
            </span>
          ))}

          {ids.length < MAX_COMPARE_IDS && resolvedLevel ? (
            <span className="compare-chip compare-chip-hint">
              {getCompareLevelMessage(resolvedLevel)}
            </span>
          ) : null}
        </div>
      )}

      {!validation.valid && validation.message ? (
        <div className="compare-validation-banner" role="status">
          <span>{validation.message}</span>
          {standalone ? (
            <a href="/" className="compare-return-link">
              ← Return to the map
            </a>
          ) : null}
        </div>
      ) : null}

      {ids.length === 0 && resolvedLevel ? (
        <div className="compare-empty-prompt">
          {getCompareLevelMessage(resolvedLevel)}
        </div>
      ) : null}

      {loading ? (
        <div className="compare-loading" aria-live="polite">
          Loading comparison…
        </div>
      ) : null}

      {!loading && validation.valid && results ? (
        <div className="compare-cards-grid">
          {results.map((result) => {
            if (result.status === "rejected" || !result.area) {
              return (
                <div key={result.id} className="compare-card compare-card--error">
                  <p className="compare-card-error-message">
                    Could not load area {result.id}.
                  </p>
                  <p className="compare-card-error-detail">{result.error}</p>
                </div>
              );
            }

            const area = result.area;

            return (
              <div key={result.id} className="compare-card">
                <h3 className="compare-card-title">
                  {getAreaDisplayName(area, result.id)}
                </h3>

                <div className="compare-card-metric">
                  <span className="compare-card-metric-label">Identifier</span>
                  <span className="compare-card-metric-value">{result.id}</span>
                </div>

                <p className="compare-card-error-detail">
                  Summary metrics can be added once this component is mapped to the
                  real AreaResponse shape.
                </p>

                {resolvedLevel ? (
                  <a
                    href={getAreaDetailHref(resolvedLevel, result.id)}
                    className="compare-card-detail-link"
                  >
                    Open full details →
                  </a>
                ) : null}
              </div>
            );
          })}
        </div>
      ) : null}
    </div>
  );
}