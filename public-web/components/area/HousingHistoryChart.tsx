// public-web/components/area/HousingHistoryChart.tsx
import type { HistoryPoint } from "@/lib/api";

interface Props {
  points: HistoryPoint[];
  title?: string;
}

function formatCurrencyLabel(value: number): string {
  return new Intl.NumberFormat("en-US", {
    style: "currency",
    currency: "USD",
    maximumFractionDigits: 0,
    notation: "compact",
  }).format(value);
}

export function HousingHistoryChart({
  points,
  title = "Home value history",
}: Props) {
  if (points.length < 2) {
    return (
      <section className="detail-section detail-chart-section">
        <h2 className="detail-section-title">{title}</h2>
        <p className="detail-chart-nodata">
          No home value history is available yet for this area.
        </p>
      </section>
    );
  }

  const width = 600;
  const height = 220;
  const paddingTop = 20;
  const paddingRight = 20;
  const paddingBottom = 40;
  const paddingLeft = 60;

  const values = points
    .map((point) => point.value)
    .filter((value): value is number => value !== null);

  if (values.length < 2) {
    return (
      <section className="detail-section detail-chart-section">
        <h2 className="detail-section-title">{title}</h2>
        <p className="detail-chart-nodata">
          No home value history is available yet for this area.
        </p>
      </section>
    );
  }

  const minValue = Math.min(...values);
  const maxValue = Math.max(...values);
  const valueRange = Math.max(maxValue - minValue, 1);

  const chartWidth = width - paddingLeft - paddingRight;
  const chartHeight = height - paddingTop - paddingBottom;

  const xForIndex = (index: number) =>
    paddingLeft + (chartWidth * index) / (points.length - 1);

  const yForValue = (value: number) =>
    paddingTop + chartHeight - ((value - minValue) / valueRange) * chartHeight;

  const polylinePoints = points
    .map((point, index) => {
      if (point.value === null) {
        return null;
      }
      return `${xForIndex(index)},${yForValue(point.value)}`;
    })
    .filter((value): value is string => value !== null)
    .join(" ");

  const yAxisTicks = Array.from({ length: 4 }, (_, index) => {
    const ratio = index / 3;
    const value = maxValue - valueRange * ratio;
    const y = paddingTop + chartHeight * ratio;

    return {
      y,
      label: formatCurrencyLabel(value),
    };
  });

  const xAxisLabels = [
    points[0],
    points[Math.floor(points.length / 2)],
    points[points.length - 1],
  ];

  return (
    <section className="detail-section detail-chart-section">
      <h2 className="detail-section-title">{title}</h2>

      <div className="detail-chart-svg-wrap">
        <svg
          className="detail-chart-svg"
          viewBox={`0 0 ${width} ${height}`}
          role="img"
          aria-label="Home value history line chart"
          preserveAspectRatio="none"
        >
          {yAxisTicks.map((tick) => (
            <g key={tick.label}>
              <line
                x1={paddingLeft}
                x2={width - paddingRight}
                y1={tick.y}
                y2={tick.y}
                stroke="#e2e8f0"
                strokeWidth="1"
              />
              <text
                x={paddingLeft - 8}
                y={tick.y + 4}
                textAnchor="end"
                className="detail-chart-axis-label"
              >
                {tick.label}
              </text>
            </g>
          ))}

          <polyline
            fill="none"
            stroke="var(--color-accent)"
            strokeWidth="3"
            points={polylinePoints}
          />

          {points.map((point, index) => {
            if (point.value === null) {
              return null;
            }

            return (
              <circle
                key={`${point.snapshotPeriod}-${index}`}
                cx={xForIndex(index)}
                cy={yForValue(point.value)}
                r="4"
                fill="var(--color-accent)"
              />
            );
          })}

          {xAxisLabels.map((point, index) => (
            <text
              key={`${point.snapshotPeriod}-${index}`}
              x={
                index === 0
                  ? xForIndex(0)
                  : index === 1
                  ? xForIndex(Math.floor(points.length / 2))
                  : xForIndex(points.length - 1)
              }
              y={height - 10}
              textAnchor={index === 0 ? "start" : index === 2 ? "end" : "middle"}
              className="detail-chart-axis-label"
            >
              {point.snapshotPeriod.slice(0, 4)}
            </text>
          ))}
        </svg>
      </div>
    </section>
  );
}