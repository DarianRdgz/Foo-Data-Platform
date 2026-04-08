// public-web/lib/route-contract.ts
import { isKnownStateCode } from "@/lib/us-states";

/**
 * All geo levels currently exposed by the public area/search API.
 */
export type GeoLevel =
  | "national"
  | "state"
  | "metro"
  | "county"
  | "city"
  | "zip";

/**
 * Geo levels allowed by the canonical public detail route during beta.
 * City pages are intentionally deferred.
 */
export type AreaDetailGeoLevel = "state" | "county" | "metro" | "zip";

/**
 * Geo levels allowed by the map tile endpoint.
 */
export type MapTileGeoLevel = "state" | "county" | "metro" | "zip";

const AREA_DETAIL_LEVELS = new Set<AreaDetailGeoLevel>([
  "state",
  "county",
  "metro",
  "zip",
]);

const TWO_DIGIT_RE = /^\d{2}$/;
const FIVE_DIGIT_RE = /^\d{5}$/;

/**
 * Public state routes use real 2-digit state FIPS codes only.
 * Regex-only validation is not enough; the code must exist in the approved set.
 */
export function isValidStateCode(value: string): boolean {
  return TWO_DIGIT_RE.test(value) && isKnownStateCode(value);
}

export function isValidAreaDetailGeoLevel(
  value: string
): value is AreaDetailGeoLevel {
  return AREA_DETAIL_LEVELS.has(value as AreaDetailGeoLevel);
}

/**
 * Identifier rules from docs/public-route-contract.txt
 *
 * state  -> 2-digit state FIPS
 * county -> 5-digit county FIPS
 * metro  -> 5-digit CBSA code
 * zip    -> 5-digit ZIP code
 */
export function isValidAreaIdentifier(
  level: AreaDetailGeoLevel,
  identifier: string
): boolean {
  switch (level) {
    case "state":
      return isValidStateCode(identifier);
    case "county":
    case "metro":
    case "zip":
      return FIVE_DIGIT_RE.test(identifier);
    default:
      return false;
  }
}