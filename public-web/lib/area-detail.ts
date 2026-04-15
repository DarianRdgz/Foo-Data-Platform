// public-web/lib/area-detail.ts
import type {
  AreaResponse,
  AreaSnapshot,
  HistoryPoint,
  HistoryResponse,
} from "@/lib/api";
import type { AreaDetailGeoLevel } from "@/lib/route-contract";

export interface AreaDetailMetric {
  label: string;
  valueText: string;
  available: boolean;
  period: string | null;
}

export interface AreaDetailSection {
  id: string;
  label: string;
  metrics: AreaDetailMetric[];
}

export interface AreaDetailModel {
  displayLabel: string;
  geoLevel: Exclude<AreaDetailGeoLevel, "zip">;
  identifier: string;
  parentStateCode: string | null;
  parentStateHref: string | null;
  sections: AreaDetailSection[];
  historyPoints: HistoryPoint[];
  hasHistory: boolean;
}

type MetricKind = "currency" | "percent" | "integer" | "decimal";

function getCanonicalIdentifier(area: AreaResponse): string {
  switch (area.geoLevel) {
    case "state":
      return area.fipsCode ?? area.geoId;
    case "county":
      return area.fipsCode ?? area.geoId;
    case "metro":
      return area.cbsaCode ?? area.geoId;
    case "zip":
      return area.zipCode ?? area.geoId;
    default:
      return area.geoId;
  }
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

function formatMetricValue(kind: MetricKind, value: number): string {
  switch (kind) {
    case "currency":
      return new Intl.NumberFormat("en-US", {
        style: "currency",
        currency: "USD",
        maximumFractionDigits: 0,
      }).format(value);
    case "percent":
      return `${value.toFixed(1)}%`;
    case "integer":
      return new Intl.NumberFormat("en-US", {
        maximumFractionDigits: 0,
      }).format(value);
    case "decimal":
      return value.toFixed(1);
    default:
      return String(value);
  }
}

function makeMetric(
  snapshot: AreaSnapshot | null,
  label: string,
  kind: MetricKind,
  ...keys: string[]
): AreaDetailMetric {
  const value = getNumberFromPayload(snapshot, ...keys);

  if (value === null) {
    return {
      label,
      valueText: "Not available",
      available: false,
      period: snapshot?.snapshotPeriod ?? null,
    };
  }

  return {
    label,
    valueText: formatMetricValue(kind, value),
    available: true,
    period: snapshot?.snapshotPeriod ?? null,
  };
}

function filterSnapshots(area: AreaResponse): AreaSnapshot[] {
  return area.snapshots.filter((snapshot) => snapshot.isRollup !== true);
}

function makeSnapshotMap(area: AreaResponse): Map<string, AreaSnapshot> {
  return new Map(
    filterSnapshots(area).map((snapshot) => [snapshot.category, snapshot] as const)
  );
}

function pushSectionIfAnyAvailable(
  sections: AreaDetailSection[],
  section: AreaDetailSection
): void {
  if (section.metrics.some((metric) => metric.available)) {
    sections.push(section);
  }
}

function buildHousingSection(
  snapshots: Map<string, AreaSnapshot>
): AreaDetailSection {
  return {
    id: "housing",
    label: "Housing",
    metrics: [
      makeMetric(
        snapshots.get("housing.home_value") ?? null,
        "Home value",
        "currency",
        "value",
        "median"
      ),
      makeMetric(
        snapshots.get("housing.rent_index") ?? null,
        "Rent index",
        "currency",
        "value",
        "indexValue"
      ),
    ],
  };
}

function buildEducationSection(
  snapshots: Map<string, AreaSnapshot>
): AreaDetailSection {
  return {
    id: "education",
    label: "Education",
    metrics: [
      makeMetric(
        snapshots.get("education.postsecondary.school_count") ?? null,
        "School count",
        "integer",
        "value",
        "schoolCount"
      ),
      makeMetric(
        snapshots.get("education.postsecondary.avg_admission_rate") ?? null,
        "Avg admission rate",
        "percent",
        "value",
        "avgAdmissionRate"
      ),
      makeMetric(
        snapshots.get("education.postsecondary.avg_net_price") ?? null,
        "Avg net price",
        "currency",
        "value",
        "avgNetPrice"
      ),
      makeMetric(
        snapshots.get("education.postsecondary.avg_median_earnings_10yr") ?? null,
        "Median earnings (10yr)",
        "currency",
        "value",
        "avgMedianEarnings10yr",
        "medianEarnings10yr"
      ),
      makeMetric(
        snapshots.get("education.postsecondary.pct_with_financial_aid") ?? null,
        "With financial aid",
        "percent",
        "value",
        "pctWithFinancialAid"
      ),
    ],
  };
}

function buildEconomicSection(
  snapshots: Map<string, AreaSnapshot>
): AreaDetailSection {
  return {
    id: "economic",
    label: "Economic",
    metrics: [
      makeMetric(
        snapshots.get("economic.unemployment_rate") ?? null,
        "Unemployment rate",
        "percent",
        "value",
        "rate"
      ),
      makeMetric(
        snapshots.get("economic.gdp_real") ?? null,
        "Real GDP",
        "currency",
        "value",
        "gdp"
      ),
    ],
  };
}

function buildRiskSection(
  snapshots: Map<string, AreaSnapshot>
): AreaDetailSection {
  return {
    id: "risk",
    label: "Risk and disasters",
    metrics: [
      makeMetric(
        snapshots.get("risk.composite") ?? null,
        "Composite risk",
        "decimal",
        "riskScore",
        "value"
      ),
      makeMetric(
        snapshots.get("risk.disaster.fema") ?? null,
        "FEMA disaster score",
        "decimal",
        "riskScore",
        "value"
      ),
      makeMetric(
        snapshots.get("risk.disaster.noaa") ?? null,
        "NOAA disaster score",
        "decimal",
        "riskScore",
        "value"
      ),
    ],
  };
}

function buildListingsSection(
  snapshots: Map<string, AreaSnapshot>
): AreaDetailSection {
  return {
    id: "listings",
    label: "Listings",
    metrics: [
      makeMetric(
        snapshots.get("housing.for_sale_inventory") ?? null,
        "For-sale inventory",
        "integer",
        "value",
        "count"
      ),
      makeMetric(
        snapshots.get("housing.median_list_price") ?? null,
        "Median list price",
        "currency",
        "value",
        "median"
      ),
      makeMetric(
        snapshots.get("housing.new_listings") ?? null,
        "New listings",
        "integer",
        "value",
        "count"
      ),
      makeMetric(
        snapshots.get("housing.newly_pending") ?? null,
        "Newly pending",
        "integer",
        "value",
        "count"
      ),
      makeMetric(
        snapshots.get("housing.days_to_pending") ?? null,
        "Days to pending",
        "integer",
        "value",
        "days"
      ),
      makeMetric(
        snapshots.get("housing.market_heat_index") ?? null,
        "Market heat index",
        "decimal",
        "value",
        "index"
      ),
      makeMetric(
        snapshots.get("housing.median_sale_price") ?? null,
        "Median sale price",
        "currency",
        "value",
        "median"
      ),
      makeMetric(
        snapshots.get("housing.sales_count") ?? null,
        "Sales count",
        "integer",
        "value",
        "count"
      ),
      makeMetric(
        snapshots.get("housing.share_with_price_cut") ?? null,
        "Share with price cut",
        "percent",
        "value",
        "share"
      ),
    ],
  };
}

function buildAffordabilitySection(
  snapshots: Map<string, AreaSnapshot>
): AreaDetailSection {
  return {
    id: "affordability",
    label: "Affordability",
    metrics: [
      makeMetric(
        snapshots.get("housing.affordability.income_needed") ?? null,
        "Income needed",
        "currency",
        "value",
        "incomeNeeded"
      ),
      makeMetric(
        snapshots.get("housing.affordability.ratio") ?? null,
        "Affordability ratio",
        "decimal",
        "value",
        "ratio"
      ),
      makeMetric(
        snapshots.get("housing.affordability.years_to_save") ?? null,
        "Years to save",
        "decimal",
        "value",
        "yearsToSave"
      ),
    ],
  };
}

function buildHistoryPoints(history: HistoryResponse | null): HistoryPoint[] {
  if (!history) {
    return [];
  }

  return history.points.filter((point) => point.value !== null);
}

export function buildAreaDetailModel(
  area: AreaResponse,
  history: HistoryResponse | null
): AreaDetailModel {
  const snapshots = makeSnapshotMap(area);
  const sections: AreaDetailSection[] = [];
  const historyPoints = buildHistoryPoints(history);

  const geoLevel = area.geoLevel;
  const identifier = getCanonicalIdentifier(area);
  const parentStateCode =
    geoLevel === "county" ? (area.fipsCode?.slice(0, 2) ?? null) : null;

  if (geoLevel === "state") {
    sections.push(buildHousingSection(snapshots));
    sections.push(buildEducationSection(snapshots));
    pushSectionIfAnyAvailable(sections, buildEconomicSection(snapshots));
    sections.push(buildRiskSection(snapshots));
  }

  if (geoLevel === "county") {
    sections.push(buildHousingSection(snapshots));
    sections.push(buildRiskSection(snapshots));
  }

  if (geoLevel === "metro") {
    sections.push(buildHousingSection(snapshots));
    pushSectionIfAnyAvailable(sections, buildListingsSection(snapshots));
    pushSectionIfAnyAvailable(sections, buildAffordabilitySection(snapshots));
    pushSectionIfAnyAvailable(sections, buildEconomicSection(snapshots));
  }

  return {
    displayLabel: area.displayLabel,
    geoLevel: geoLevel as Exclude<AreaDetailGeoLevel, "zip">,
    identifier,
    parentStateCode,
    parentStateHref: parentStateCode
      ? `/area/state/${parentStateCode}`
      : null,
    sections,
    historyPoints,
    hasHistory: historyPoints.length >= 2,
  };
}