import type { ComparableGeoLevel } from "@/lib/compare-validation";
import { isComparableGeoLevel } from "@/lib/compare-validation";
import type { HomeUrlState } from "@/lib/home-query";
import { deriveCompareLevelFromContext } from "@/lib/home-query";

export interface DirectCompareState {
  level: ComparableGeoLevel | null;
  ids: string[];
}

export function parseDirectCompareParams(input: {
  level?: string | null;
  ids?: string | null;
}): DirectCompareState {
  const rawIds = (input.ids ?? "")
    .split(",")
    .map((value) => value.trim())
    .filter(Boolean);

  const seen = new Set<string>();
  const ids: string[] = [];

  for (const id of rawIds) {
    if (seen.has(id)) {
      continue;
    }
    seen.add(id);
    ids.push(id);
  }

  if (input.level && isComparableGeoLevel(input.level)) {
    return {
      level: input.level,
      ids,
    };
  }

  const allStateIds =
    ids.length > 0 && ids.every((id) => /^\d{2}$/.test(id));

  if (!input.level && allStateIds) {
    return {
      level: "state",
      ids,
    };
  }

  return {
    level: null,
    ids,
  };
}

export function buildDirectCompareUrl(
  state: DirectCompareState,
  pathname = "/compare"
): string {
  const params = new URLSearchParams();

  if (state.level) {
    params.set("level", state.level);
  }

  if (state.ids.length > 0) {
    params.set("ids", state.ids.join(","));
  }

  const query = params.toString();
  return query ? `${pathname}?${query}` : pathname;
}

export function deriveDirectCompareStateFromHomeState(
  homeState: HomeUrlState
): DirectCompareState {
  return {
    level: deriveCompareLevelFromContext(
      homeState.selectedStateFips,
      homeState.selectedBrowseLevel
    ),
    ids: homeState.compareIds,
  };
}

export function buildDirectCompareUrlFromHomeState(
  homeState: HomeUrlState
): string {
  return buildDirectCompareUrl(
    deriveDirectCompareStateFromHomeState(homeState)
  );
}