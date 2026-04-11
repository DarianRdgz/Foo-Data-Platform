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

  const quickFacts = summary?.metrics.slice(0, 3) ?? [];

  return (
    <div
      className="state-modal-backdrop"
      role="presentation"
      onClick={onClose}
      onKeyDown={(event) => {
        if (event.key === "Escape") {
          onClose();
        }
      }}
    >
      <div
        className="state-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="state-modal-title"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="state-modal-header">
          <div>
            <p className="state-modal-kicker">Selected state</p>
            <h2 id="state-modal-title">{stateName}</h2>
            <p className="state-modal-subtitle">
              The summary panel has updated. Continue into county view when you are ready.
            </p>
          </div>

          <button
            ref={closeButtonRef}
            type="button"
            className="state-modal-close"
            aria-label="Close state quick view"
            onClick={onClose}
          >
            ×
          </button>
        </div>

        {quickFacts.length > 0 ? (
          <div className="state-modal-grid">
            {quickFacts.map((metric) => (
              <article key={metric.category} className="state-modal-card">
                <span className="state-modal-label">{metric.label}</span>
                <strong className="state-modal-value">{metric.valueText}</strong>
                <span className="state-modal-detail">
                  {metric.detailText ??
                    (metric.available ? "" : "Not available yet")}
                </span>
              </article>
            ))}
          </div>
        ) : (
          <p className="state-modal-empty">Quick facts are not available yet.</p>
        )}

        <div className="state-modal-actions">
          <button
            type="button"
            className="btn-secondary"
            onClick={onClose}
          >
            Stay on U.S. map
          </button>

          <button
            type="button"
            className="btn-primary"
            onClick={onEnterCountyView}
          >
            View counties
          </button>
        </div>
      </div>
    </div>
  );
}