export type ComparableGeoLevel = "state" | "county" | "metro";

export type CompareValidationStatus =
  | "empty"
  | "partial"
  | "valid"
  | "over_limit"
  | "mixed_level"
  | "unsupported_level"
  | "bad_id_format";

export interface CompareValidationResult {
  status: CompareValidationStatus;
  valid: boolean;
  message: string | null;
}

export const MIN_COMPARE_IDS = 2;
export const MAX_COMPARE_IDS = 4;

const STATE_FIPS_RE = /^\d{2}$/;
const COUNTY_FIPS_RE = /^\d{5}$/;
const METRO_CBSA_RE = /^\d{5}$/;

const COMPARABLE_LEVELS = new Set<string>(["state", "county", "metro"]);

export function isComparableGeoLevel(value: string): value is ComparableGeoLevel {
  return COMPARABLE_LEVELS.has(value);
}

function getExpectedPattern(level: ComparableGeoLevel): RegExp {
  switch (level) {
    case "state":
      return STATE_FIPS_RE;
    case "county":
      return COUNTY_FIPS_RE;
    case "metro":
      return METRO_CBSA_RE;
  }
}

export function validateCompareRequest(
  level: string | null | undefined,
  ids: string[]
): CompareValidationResult {
  if (!level || !isComparableGeoLevel(level)) {
    return {
      status: "unsupported_level",
      valid: false,
      message:
        level
          ? `"${level}" is not a supported compare level. Choose state, county, or metro.`
          : "A compare level is required.",
    };
  }

  if (ids.length === 0) {
    return {
      status: "empty",
      valid: false,
      message: "Choose at least two areas to compare.",
    };
  }

  if (ids.length === 1) {
    return {
      status: "partial",
      valid: false,
      message: "Add one more area to start comparing.",
    };
  }

  if (ids.length > MAX_COMPARE_IDS) {
    return {
      status: "over_limit",
      valid: false,
      message: `Compare supports up to ${MAX_COMPARE_IDS} areas. You selected ${ids.length}.`,
    };
  }

  const pattern = getExpectedPattern(level);
  const badIds = ids.filter((id) => !pattern.test(id));

  if (badIds.length > 0) {
    return {
      status: "bad_id_format",
      valid: false,
      message: `One or more selected IDs do not match the expected format for ${level} compare.`,
    };
  }

  return {
    status: "valid",
    valid: true,
    message: null,
  };
}

export function getCompareLevelMessage(level: ComparableGeoLevel): string {
  switch (level) {
    case "state":
      return "Click states on the map to add them to your comparison.";
    case "county":
      return "Click counties on the map to add them to your comparison.";
    case "metro":
      return "Click metro areas on the map to add them to your comparison.";
  }
}

export function normalizeCompareIds(
  level: ComparableGeoLevel,
  ids: string[]
): string[] {
  const pattern = getExpectedPattern(level);
  const seen = new Set<string>();
  const result: string[] = [];

  for (const id of ids) {
    const trimmed = id.trim();
    if (!pattern.test(trimmed)) continue;
    if (seen.has(trimmed)) continue;
    seen.add(trimmed);
    result.push(trimmed);
    if (result.length === MAX_COMPARE_IDS) break;
  }

  return result;
}