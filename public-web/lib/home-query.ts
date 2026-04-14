// public-web/lib/home-query.ts
import type { ComparableGeoLevel } from "@/lib/compare-validation";
import {
  isComparableGeoLevel,
  normalizeCompareIds as normalizeValidatedCompareIds,
} from "@/lib/compare-validation";
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

/**
 * Derive the compare level from the current homepage context.
 * - No selected state => compare states
 * - Selected state + metro view => compare metros
 * - Selected state + any other supported local view => compare counties
 */
export function deriveCompareLevelFromContext(
  selectedStateFips: string | null,
  selectedBrowseLevel: HomeBrowseLevel
): ComparableGeoLevel {
  // National view compares states.
  // Selected-state summary view still shows the national state map,
  // so it also compares states.
  if (!selectedStateFips || selectedBrowseLevel === "state") {
    return "state";
  }

  if (selectedBrowseLevel === "metro") {
    return "metro";
  }

  return "county";
}

/**
 * Level-aware compare ID normalization.
 * Falls back to [] when the provided level cannot be compared.
 */
function normalizeCompareIds(
  level: HomeBrowseLevel | ComparableGeoLevel,
  ids: string[]
): string[] {
  if (!isComparableGeoLevel(level)) {
    return [];
  }

  return normalizeValidatedCompareIds(level, ids);
}

/**
 * Returns a user-facing message when the compare level changes.
 */
export function detectCompareLevelChange(
  prevLevel: ComparableGeoLevel,
  nextLevel: ComparableGeoLevel
): string | null {
  if (prevLevel === nextLevel) {
    return null;
  }

  return "Your previous comparison was cleared because the area level changed.";
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

  const compareLevel = deriveCompareLevelFromContext(
    selectedStateFips,
    selectedBrowseLevel
  );

  const compareIds = normalizeCompareIds(compareLevel, input.compareIds ?? []);

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

  if (normalized.selectedStateFips) {
    params.set("state", normalized.selectedStateFips);
  }

  if (normalized.selectedBrowseLevel !== "state") {
    params.set("level", normalized.selectedBrowseLevel);
  }

  if (normalized.tab === "compare") {
    params.set("tab", "compare");

    if (normalized.compareIds.length > 0) {
      params.set("ids", normalized.compareIds.join(","));
    }
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
    const compareLevel = deriveCompareLevelFromContext(
      normalized.selectedStateFips,
      normalized.selectedBrowseLevel
    );

    const seededIds =
      normalized.compareIds.length > 0
        ? normalizeCompareIds(compareLevel, normalized.compareIds)
        : compareLevel === "state" && normalized.selectedStateFips
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
  id: string,
  level: ComparableGeoLevel
): HomeUrlState {
  const normalized = normalizeHomeState(state);

  const currentLevel = deriveCompareLevelFromContext(
    normalized.selectedStateFips,
    normalized.selectedBrowseLevel
  );

  if (level !== currentLevel) {
    return normalized;
  }

  const alreadySelected = normalized.compareIds.includes(id);

  if (alreadySelected) {
    return normalizeHomeState({
      ...normalized,
      compareIds: normalized.compareIds.filter((value) => value !== id),
    });
  }

  if (normalized.compareIds.length >= MAX_COMPARE_IDS) {
    return normalized;
  }

  return normalizeHomeState({
    ...normalized,
    compareIds: [...normalized.compareIds, id],
  });
}