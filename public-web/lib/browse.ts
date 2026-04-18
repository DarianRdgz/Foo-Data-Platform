import type { ChildArea } from "@/lib/api";
import { buildHomeUrl } from "@/lib/home-query";

export type BrowseChildLevel = "county" | "metro";

export interface BrowseChildOption {
  key: string;
  href: string;
  displayLabel: string;
  geoLevel: BrowseChildLevel;
  geoLevelLabel: string;
  coverageCount: number;
  identifier: string;
}

export function resolveChildIdentifier(child: ChildArea): string | null {
  if (child.geoLevel === "county") {
    return child.fipsCode;
  }

  if (child.geoLevel === "metro") {
    return child.cbsaCode;
  }

  return null;
}

export function buildChildCanonicalHref(child: ChildArea): string | null {
  const identifier = resolveChildIdentifier(child);

  if (!identifier) {
    return null;
  }

  if (child.geoLevel !== "county" && child.geoLevel !== "metro") {
    return null;
  }

  return `/area/${child.geoLevel}/${identifier}`;
}

export function buildBrowseStateHref(stateCode: string): string {
  return `/browse/${stateCode}`;
}

export function buildCanonicalStateHref(stateCode: string): string {
  return `/area/state/${stateCode}`;
}

export function buildHomepageStateHref(stateCode: string): string {
  return buildHomeUrl({
    tab: "browse",
    selectedStateFips: stateCode,
    selectedBrowseLevel: "state",
    compareIds: [],
  });
}

export function buildHomepageChildBrowseHref(
  stateCode: string,
  childGeoLevel: string
): string {
  if (childGeoLevel === "county") {
    return buildHomeUrl({
      tab: "browse",
      selectedStateFips: stateCode,
      selectedBrowseLevel: "county",
      compareIds: [],
    });
  }

  if (childGeoLevel === "metro") {
    return buildHomeUrl({
      tab: "browse",
      selectedStateFips: stateCode,
      selectedBrowseLevel: "metro",
      compareIds: [],
    });
  }

  return buildHomepageStateHref(stateCode);
}

export function formatChildGeoLevel(level: string): string {
  if (level === "county") {
    return "Counties";
  }

  if (level === "metro") {
    return "Metros";
  }

  return "Areas";
}

export function isSupportedBrowseChildLevel(
  level: string
): level is BrowseChildLevel {
  return level === "county" || level === "metro";
}

export function toBrowseChildOption(
  child: ChildArea
): BrowseChildOption | null {
  if (!isSupportedBrowseChildLevel(child.geoLevel)) {
    return null;
  }

  const identifier = resolveChildIdentifier(child);
  const href = buildChildCanonicalHref(child);

  if (!identifier || !href) {
    return null;
  }

  return {
    key: `${child.geoLevel}:${identifier}`,
    href,
    displayLabel: child.displayLabel,
    geoLevel: child.geoLevel,
    geoLevelLabel: child.geoLevel === "county" ? "County" : "Metro",
    coverageCount: child.coverageCount,
    identifier,
  };
}

export function normalizeBrowseChildren(
  children: ChildArea[]
): BrowseChildOption[] {
  const seen = new Set<string>();
  const normalized: BrowseChildOption[] = [];

  for (const child of children) {
    const option = toBrowseChildOption(child);

    if (!option || seen.has(option.key)) {
      continue;
    }

    seen.add(option.key);
    normalized.push(option);
  }

  return normalized;
}