// public-web/lib/header-search.ts

import { searchAreas, type AreaSearchResult } from "@/lib/api";
import { getStateByCode } from "@/lib/us-states";

export type BetaSearchLevel = "state" | "county" | "metro";

export interface BetaSearchResult {
  level: BetaSearchLevel;
  results: AreaSearchResult[];
  error: boolean;
}

export interface HeaderSearchOption {
  key: string;
  href: string;
  title: string;
  geoLevel: BetaSearchLevel;
  geoLevelLabel: string;
  stateContext: string | null;
}

export const MIN_SEARCH_LENGTH = 2;
export const SEARCH_DEBOUNCE_MS = 280;
export const MAX_SEARCH_OPTIONS = 10;

const BETA_SEARCH_LEVELS: BetaSearchLevel[] = ["state", "county", "metro"];

export async function searchBetaAreas(
  query: string,
  signal?: AbortSignal
): Promise<BetaSearchResult[]> {
  const settled = await Promise.allSettled(
    BETA_SEARCH_LEVELS.map(async (level) => {
      const response = await searchAreas(query, level, signal);
      return {
        level,
        results: response.results,
        error: false,
      } satisfies BetaSearchResult;
    })
  );

  return settled.map((result, index) => {
    const level = BETA_SEARCH_LEVELS[index];

    if (result.status === "fulfilled") {
      return result.value;
    }

    return {
      level,
      results: [],
      error: true,
    };
  });
}

export function resolveSearchIdentifier(
  result: AreaSearchResult
): string | null {
  if (result.geoLevel === "state") {
    return result.fipsCode;
  }

  if (result.geoLevel === "county") {
    return result.fipsCode;
  }

  if (result.geoLevel === "metro") {
    return result.cbsaCode;
  }

  return null;
}

export function buildSearchHref(
  result: AreaSearchResult
): string | null {
  if (
    result.geoLevel !== "state" &&
    result.geoLevel !== "county" &&
    result.geoLevel !== "metro"
  ) {
    return null;
  }

  const identifier = resolveSearchIdentifier(result);

  if (!identifier) {
    return null;
  }

  return `/area/${result.geoLevel}/${identifier}`;
}

export function deriveStateContext(
  result: AreaSearchResult
): string | null {
  if (result.geoLevel === "state") {
    return getStateByCode(result.fipsCode ?? "")?.name ?? null;
  }

  if (result.geoLevel === "county") {
    return getStateByCode((result.fipsCode ?? "").slice(0, 2))?.name ?? null;
  }

  if (result.geoLevel === "metro") {
    return null;
  }

  return null;
}

export function formatGeoLevelLabel(level: BetaSearchLevel): string {
  if (level === "state") {
    return "State";
  }

  if (level === "county") {
    return "County";
  }

  return "Metro";
}

export function toHeaderSearchOption(
  result: AreaSearchResult
): HeaderSearchOption | null {
  if (
    result.geoLevel !== "state" &&
    result.geoLevel !== "county" &&
    result.geoLevel !== "metro"
  ) {
    return null;
  }

  const href = buildSearchHref(result);

  if (!href) {
    return null;
  }

  const identifier = resolveSearchIdentifier(result);

  if (!identifier) {
    return null;
  }

  return {
    key: `${result.geoLevel}:${identifier}`,
    href,
    title: result.displayLabel || result.name,
    geoLevel: result.geoLevel,
    geoLevelLabel: formatGeoLevelLabel(result.geoLevel),
    stateContext: deriveStateContext(result),
  };
}

export function mergeSearchResults(
  levelResults: BetaSearchResult[]
): HeaderSearchOption[] {
  const orderedLevels: BetaSearchLevel[] = ["state", "county", "metro"];
  const seen = new Set<string>();
  const merged: HeaderSearchOption[] = [];

  for (const level of orderedLevels) {
    const match = levelResults.find((item) => item.level === level);

    if (!match) {
      continue;
    }

    for (const result of match.results) {
      const option = toHeaderSearchOption(result);

      if (!option || seen.has(option.key)) {
        continue;
      }

      seen.add(option.key);
      merged.push(option);

      if (merged.length >= MAX_SEARCH_OPTIONS) {
        return merged;
      }
    }
  }

  return merged;
}

export function allLevelsFailed(
  levelResults: BetaSearchResult[]
): boolean {
  return levelResults.length > 0 && levelResults.every((item) => item.error);
}