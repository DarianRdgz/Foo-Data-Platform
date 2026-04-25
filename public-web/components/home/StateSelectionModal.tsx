// public-web/components/home/StateSelectionModal.tsx
"use client";

import type { HomeSummaryModel } from "@/lib/home-summary";
import { useEffect, useRef } from "react";

interface Props {
  isOpen: boolean;
  stateName: string | null;
  summary: HomeSummaryModel | null;
  onClose: () => void;
  onEnterCountyView: () => void;
}

export default function StateSelectionModal({
  isOpen,
  stateName,
  summary,
  onClose,
  onEnterCountyView,
}: Props) {
  const closeButtonRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (isOpen) {
      closeButtonRef.current?.focus();
    }
  }, [isOpen]);

  if (!isOpen || !stateName) {
    return null;
  }

  const quickFacts =
    summary?.metrics
      .filter((metric) => metric.available)
      .slice(0, 3)
      .map((metric) => ({
        label: metric.label,
        value: metric.valueText,
      })) ?? [];

  return (
    <aside className="state-quick-view" aria-label={`Quick view: ${stateName}`}>
      <div className="state-quick-view-header">
        <div>
          <p className="state-quick-view-kicker">Selected state</p>
          <h2 className="state-quick-view-title">{stateName}</h2>
          <p className="state-quick-view-subtitle">
            The summary panel has updated. Continue into county view when you are ready.
          </p>
        </div>
        <button
          type="button"
          className="state-quick-view-close"
          aria-label="Close state quick view"
          onClick={onClose}
          ref={closeButtonRef}
        >
          ×
        </button>
      </div>

      {quickFacts.length > 0 ? (
        <div className="state-quick-view-grid">
          {quickFacts.map((metric) => (
            <div key={metric.label} className="state-quick-view-stat">
              <span className="state-quick-view-stat-label">{metric.label}</span>
              <span className="state-quick-view-stat-value">{metric.value}</span>
            </div>
          ))}
        </div>
      ) : (
        <p className="state-quick-view-empty">Quick facts are not available yet.</p>
      )}

      <div className="state-quick-view-actions">
        <button type="button" className="btn-secondary" onClick={onClose}>
          Stay on state summary
        </button>
        <button type="button" className="btn-primary" onClick={onEnterCountyView}>
          View counties
        </button>
      </div>
    </aside>
  );
}