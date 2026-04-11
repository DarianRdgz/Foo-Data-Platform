// public-web/lib/home-query.ts
import { isKnownStateCode } from "@/lib/us-states";

export type HomeTab = "browse" | "compare";
export type HomeBrowseLevel = "state" | "county" | "metro" | "zip";

export interface HomeUrlState {
  tab: HomeTab;
  selectedStateFips: string | null;
  selectedBrowseLevel: HomeBrowseLevel;
  compareIds: string[];
}

export const MAX_COMPARE_IDS = 4;

export const DEFAULT_HOME_STATE: HomeUrlState = {
  tab: "browse",
  selectedStateFips: null,
  selectedBrowseLevel: "state",
  compareIds: [],
};

function isHomeTab(value: string | null): value is HomeTab {
  return value === "browse" || value === "compare";
}

function isHomeBrowseLevel(value: string | null): value is HomeBrowseLevel {
  return (
    value === "state" ||
    value === "county" ||
    value === "metro" ||
    value === "zip"
  );
}

function normalizeCompareIds(ids: string[]): string[] {
  const seen = new Set<string>();
  const normalized: string[] = [];

  for (const value of ids) {
    const trimmed = value.trim();

    if (!isKnownStateCode(trimmed)) {
      continue;
    }

    if (seen.has(trimmed)) {
      continue;
    }

    seen.add(trimmed);
    normalized.push(trimmed);

    if (normalized.length === MAX_COMPARE_IDS) {
      break;
    }
  }

  return normalized;
}

export function normalizeHomeState(
  input: Partial<HomeUrlState>
): HomeUrlState {
  const tab = isHomeTab(input.tab ?? null) ? input.tab! : "browse";

  const selectedStateFips =
    typeof input.selectedStateFips === "string" &&
    isKnownStateCode(input.selectedStateFips)
      ? input.selectedStateFips
      : null;

  const selectedBrowseLevel = isHomeBrowseLevel(
    input.selectedBrowseLevel ?? null
  )
    ? input.selectedBrowseLevel!
    : "state";

  const compareIds = normalizeCompareIds(input.compareIds ?? []);

  return {
    tab,
    selectedStateFips,
    selectedBrowseLevel,
    compareIds,
  };
}

export function parseHomeUrlState(searchParams: URLSearchParams): HomeUrlState {
  const rawTab = searchParams.get("tab");
  const rawBrowseLevel = searchParams.get("level");
  const rawIds = (searchParams.get("ids") ?? "")
    .split(",")
    .map((value) => value.trim())
    .filter(Boolean);

  return normalizeHomeState({
    tab: isHomeTab(rawTab) ? rawTab : undefined,
    selectedStateFips: searchParams.get("state"),
    selectedBrowseLevel: isHomeBrowseLevel(rawBrowseLevel)
      ? rawBrowseLevel
      : undefined,
    compareIds: rawIds,
  });
}

export function buildHomeUrl(
  state: HomeUrlState,
  pathname = "/"
): string {
  const normalized = normalizeHomeState(state);

  const isDefault =
    normalized.tab === "browse" &&
    normalized.selectedStateFips === null &&
    normalized.selectedBrowseLevel === "state" &&
    normalized.compareIds.length === 0;

  if (isDefault) {
    return pathname;
  }

  const params = new URLSearchParams();

  if (normalized.selectedBrowseLevel !== "state") {
    params.set("level", normalized.selectedBrowseLevel);
  }

  if (normalized.tab === "compare") {
    params.set("tab", "compare");

    if (normalized.compareIds.length > 0) {
      params.set("ids", normalized.compareIds.join(","));
    }
  } else if (normalized.selectedStateFips) {
    params.set("state", normalized.selectedStateFips);
  }

  const query = params.toString();
  return query ? `${pathname}?${query}` : pathname;
}

export function switchHomeTab(
  state: HomeUrlState,
  nextTab: HomeTab
): HomeUrlState {
  const normalized = normalizeHomeState(state);

  if (normalized.tab === nextTab) {
    return normalized;
  }

  if (nextTab === "compare") {
    const seededIds =
      normalized.compareIds.length > 0
        ? normalized.compareIds
        : normalized.selectedStateFips
        ? [normalized.selectedStateFips]
        : [];

    return normalizeHomeState({
      ...normalized,
      tab: "compare",
      compareIds: seededIds,
    });
  }

  const promotedState =
    normalized.selectedStateFips ??
    normalized.compareIds.find((value) => isKnownStateCode(value)) ??
    null;

  return normalizeHomeState({
    ...normalized,
    tab: "browse",
    selectedStateFips: promotedState,
  });
}

export function selectBrowseState(
  state: HomeUrlState,
  stateFips: string
): HomeUrlState {
  const normalized = normalizeHomeState(state);

  if (!isKnownStateCode(stateFips)) {
    return normalized;
  }

  return normalizeHomeState({
    ...normalized,
    tab: "browse",
    selectedStateFips: stateFips,
    selectedBrowseLevel: "state",
  });
}

export function setBrowseLevel(
  state: HomeUrlState,
  nextLevel: Extract<HomeBrowseLevel, "state" | "county" | "metro">
): HomeUrlState {
  const normalized = normalizeHomeState(state);

  return normalizeHomeState({
    ...normalized,
    selectedBrowseLevel: nextLevel,
  });
}

export function clearBrowseStateFocus(state: HomeUrlState): HomeUrlState {
  return normalizeHomeState({
    ...state,
    tab: "browse",
    selectedStateFips: null,
    selectedBrowseLevel: "state",
  });
}

export function toggleCompareSelection(
  state: HomeUrlState,
  stateFips: string
): HomeUrlState {
  const normalized = normalizeHomeState(state);

  if (!isKnownStateCode(stateFips)) {
    return normalized;
  }

  const alreadySelected = normalized.compareIds.includes(stateFips);

  if (alreadySelected) {
    return normalizeHomeState({
      ...normalized,
      compareIds: normalized.compareIds.filter((value) => value !== stateFips),
    });
  }

  if (normalized.compareIds.length >= MAX_COMPARE_IDS) {
    return normalized;
  }

  return normalizeHomeState({
    ...normalized,
    compareIds: [...normalized.compareIds, stateFips],
  });
}