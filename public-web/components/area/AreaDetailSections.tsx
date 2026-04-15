// public-web/components/area/AreaDetailSections.tsx
import Link from "next/link";
import type { AreaDetailModel } from "@/lib/area-detail";
import { HousingHistoryChart } from "@/components/area/HousingHistoryChart";

interface Props {
  model: AreaDetailModel;
}

export function AreaDetailSections({ model }: Props) {
  return (
    <>
      <div className="detail-header">
        <p className="detail-eyebrow">Area detail</p>
        <h1 className="detail-title">{model.displayLabel}</h1>

        <div className="detail-meta">
          <span className="detail-badge">geoLevel: {model.geoLevel}</span>
          <span className="detail-badge">identifier: {model.identifier}</span>
          <span className="detail-badge">route: /area/[geoLevel]/[identifier]</span>
        </div>

        <div className="detail-breadcrumb">
          {model.parentStateHref ? (
            <Link href={model.parentStateHref} className="detail-link">
              ← Parent state
            </Link>
          ) : null}

          <Link href="/" className="detail-link">
            ← Home
          </Link>
        </div>
      </div>

      {model.sections.map((section) => (
        <section key={section.id} className="detail-section">
          <h2 className="detail-section-title">{section.label}</h2>

          <div className="detail-metric-grid">
            {section.metrics.map((metric) => (
              <article
                key={metric.label}
                className={`detail-metric-card ${
                  metric.available ? "" : "detail-metric-unavailable"
                }`}
              >
                <span className="detail-metric-label">{metric.label}</span>
                <strong className="detail-metric-value">{metric.valueText}</strong>
                <span className="detail-metric-period">
                  {metric.period ?? ""}
                </span>
              </article>
            ))}
          </div>
        </section>
      ))}

      <HousingHistoryChart points={model.historyPoints} />
    </>
  );
}