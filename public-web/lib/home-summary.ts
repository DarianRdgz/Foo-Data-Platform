// public-web/lib/home-summary.ts
import type { AreaResponse, AreaSnapshot } from "@/lib/api";

export interface SummaryMetricCard {
  category: string;
  label: string;
  valueText: string;
  detailText: string | null;
  available: boolean;
}

export interface HomeSummaryModel {
  title: string;
  subtitle: string;
  metrics: SummaryMetricCard[];
}

interface MetricConfig {
  category: string;
  label: string;
  buildValue: (snapshot: AreaSnapshot | null) => SummaryMetricCard;
}

function getNumberFromPayload(
  snapshot: AreaSnapshot | null,
  ...keys: string[]
): number | null {
  if (!snapshot) {
    return null;
  }

  for (const key of keys) {
    const raw = snapshot.payload[key];

    if (typeof raw === "number" && Number.isFinite(raw)) {
      return raw;
    }

    if (typeof raw === "string") {
      const parsed = Number(raw);
      if (Number.isFinite(parsed)) {
        return parsed;
      }
    }
  }

  return null;
}

function formatCurrency(value: number): string {
  return new Intl.NumberFormat("en-US", {
    style: "currency",
    currency: "USD",
    maximumFractionDigits: 0,
  }).format(value);
}

function formatPercent(value: number): string {
  return `${value.toFixed(1)}%`;
}

function buildUnavailableCard(
  category: string,
  label: string
): SummaryMetricCard {
  return {
    category,
    label,
    valueText: "Not available yet",
    detailText: null,
    available: false,
  };
}

const SUMMARY_CONFIG: MetricConfig[] = [
  {
    category: "housing.home_value",
    label: "Home value",
    buildValue(snapshot) {
      const value = getNumberFromPayload(snapshot, "value", "median");

      if (value === null) {
        return buildUnavailableCard("housing.home_value", "Home value");
      }

      return {
        category: "housing.home_value",
        label: "Home value",
        valueText: formatCurrency(value),
        detailText: snapshot?.snapshotPeriod ?? null,
        available: true,
      };
    },
  },
  {
    category: "housing.rent_index",
    label: "Rent index",
    buildValue(snapshot) {
      const value = getNumberFromPayload(snapshot, "value", "indexValue");

      if (value === null) {
        return buildUnavailableCard("housing.rent_index", "Rent index");
      }

      return {
        category: "housing.rent_index",
        label: "Rent index",
        valueText: formatCurrency(value),
        detailText: snapshot?.snapshotPeriod ?? null,
        available: true,
      };
    },
  },
  {
    category: "economic.unemployment_rate",
    label: "Unemployment",
    buildValue(snapshot) {
      const value = getNumberFromPayload(snapshot, "value", "rate");

      if (value === null) {
        return buildUnavailableCard(
          "economic.unemployment_rate",
          "Unemployment"
        );
      }

      return {
        category: "economic.unemployment_rate",
        label: "Unemployment",
        valueText: formatPercent(value),
        detailText: snapshot?.snapshotPeriod ?? null,
        available: true,
      };
    },
  },
  {
    category: "risk.composite",
    label: "Composite risk",
    buildValue(snapshot) {
      const value = getNumberFromPayload(snapshot, "riskScore", "value");
      const tier =
        snapshot && typeof snapshot.payload.tier === "string"
          ? snapshot.payload.tier
          : null;

      if (value === null) {
        return buildUnavailableCard("risk.composite", "Composite risk");
      }

      return {
        category: "risk.composite",
        label: "Composite risk",
        valueText: value.toFixed(1),
        detailText: tier ? `Tier: ${tier}` : snapshot?.snapshotPeriod ?? null,
        available: true,
      };
    },
  },
];

function buildSubtitle(area: AreaResponse): string {
  switch (area.geoLevel) {
    case "national":
      return "National summary";
    case "state":
      return "Selected state summary";
    case "county":
      return "Selected county summary";
    case "metro":
      return "Selected metro summary";
    default:
      return "Selected area summary";
  }
}

export function buildHomeSummary(area: AreaResponse): HomeSummaryModel {
  const snapshotByCategory = new Map(
    area.snapshots.map((snapshot) => [snapshot.category, snapshot] as const)
  );

  const metrics = SUMMARY_CONFIG.map((config) =>
    config.buildValue(snapshotByCategory.get(config.category) ?? null)
  );

  return {
    title: area.displayLabel,
    subtitle: buildSubtitle(area),
    metrics,
  };
}