// public-web/lib/api.ts
import type {
  AreaDetailGeoLevel,
  GeoLevel,
  MapTileGeoLevel,
} from "@/lib/route-contract";
export type {
  AreaDetailGeoLevel,
  GeoLevel,
  MapTileGeoLevel,
} from "@/lib/route-contract";

// ─── Base URL ────────────────────────────────────────────────────────────────

function getApiBaseUrl(): string {
  const value = process.env.NEXT_PUBLIC_API_BASE_URL;

  if (!value) {
    throw new Error(
      "NEXT_PUBLIC_API_BASE_URL is required. Add it to public-web/.env.local."
    );
  }

  return value.replace(/\/$/, "");
}

// ─── Geo-level types by usage ────────────────────────────────────────────────

/**
 * Stable national geo_id seeded by V11__fdp_geo_hierarchy_schema.sql.
 * Used by the homepage summary panel on default load.
 */
export const NATIONAL_GEO_ID = "00000000-0000-0000-0000-000000000000";

// ─── DTOs ────────────────────────────────────────────────────────────────────

export interface AreaParent {
  geoId: string;
  geoLevel: GeoLevel;
  name: string;
  displayLabel: string;
}

export interface AreaSnapshotChange {
  pctChange?: number;
  direction?: string;
  magnitude?: string;
}

export interface AreaSnapshot {
  category: string;
  snapshotPeriod: string;
  source: string;
  isRollup: boolean;
  payload: Record<string, unknown>;
  change: AreaSnapshotChange | null;
}

export interface AreaResponse {
  geoId: string;
  geoLevel: GeoLevel;
  name: string;
  displayLabel: string;
  fipsCode: string | null;
  cbsaCode: string | null;
  zipCode: string | null;
  parent: AreaParent | null;
  boundingBox: unknown | null;
  snapshots: AreaSnapshot[];
}

export interface HistoryPoint {
  snapshotPeriod: string;
  value: number | null;
  payload: Record<string, unknown>;
}

export interface HistoryResponse {
  geoId: string;
  geoLevel: GeoLevel;
  category: string;
  periodsRequested: number;
  points: HistoryPoint[];
}

export interface ChildArea {
  geoId: string;
  geoLevel: GeoLevel;
  name: string;
  displayLabel: string;
  coverageCount: number;
}

export interface ChildrenResponse {
  geoId: string;
  geoLevel: GeoLevel;
  childGeoLevel: GeoLevel;
  children: ChildArea[];
}

export interface AreaSearchResult {
  geoId: string;
  geoLevel: GeoLevel;
  name: string;
  displayLabel: string;
  fipsCode: string | null;
  cbsaCode: string | null;
  zipCode: string | null;
}

export interface AreaSearchResponse {
  query: string;
  level: GeoLevel;
  results: AreaSearchResult[];
}

// ─── Map tile types ───────────────────────────────────────────────────────────

export interface GeoTileProperties {
  boundaryKey: string;
  boundaryKind: string;
  displayLabel: string;
  [key: string]: unknown;
}

export interface GeoJsonFeature {
  type: "Feature";
  geometry: unknown | null;
  properties: GeoTileProperties;
}

export interface GeoJsonFeatureCollection {
  type: "FeatureCollection";
  features: GeoJsonFeature[];
}

// ─── Public error contract ───────────────────────────────────────────────────

export type PublicApiStatus =
  | "VALIDATION_ERROR"
  | "NOT_FOUND"
  | "LOCKED"
  | "API_ERROR"
  | "DB_ERROR"
  | "UNCLASSIFIED";

export interface PublicApiErrorResponse {
  status: PublicApiStatus;
  message: string;
  path: string;
  timestamp: string;
}

export class PublicApiError extends Error {
  readonly httpStatus: number;
  readonly apiStatus: PublicApiStatus | null;
  readonly path: string;
  readonly timestamp: string | null;
  readonly raw: string;

  constructor(args: {
    httpStatus: number;
    message: string;
    apiStatus: PublicApiStatus | null;
    path: string;
    timestamp: string | null;
    raw: string;
  }) {
    super(args.message);
    this.name = "PublicApiError";
    this.httpStatus = args.httpStatus;
    this.apiStatus = args.apiStatus;
    this.path = args.path;
    this.timestamp = args.timestamp;
    this.raw = args.raw;
  }

  get isNotFound(): boolean {
    return this.httpStatus === 404 || this.apiStatus === "NOT_FOUND";
  }

  get isValidationError(): boolean {
    return (
      this.httpStatus === 400 || this.apiStatus === "VALIDATION_ERROR"
    );
  }
}

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

function isPublicApiErrorResponse(
  value: unknown
): value is PublicApiErrorResponse {
  if (!isObject(value)) {
    return false;
  }

  return (
    typeof value.status === "string" &&
    typeof value.message === "string" &&
    typeof value.path === "string" &&
    typeof value.timestamp === "string"
  );
}

// ─── Core fetch helper ────────────────────────────────────────────────────────

async function apiGet<T>(path: string): Promise<T> {
  const baseUrl = getApiBaseUrl();
  const url = `${baseUrl}${path}`;

  const response = await fetch(url, {
    headers: {
      Accept: "application/json",
    },
    cache: "no-store",
  });

  if (!response.ok) {
    const raw = await response.text();
    const contentType = response.headers.get("content-type") ?? "";

    if (contentType.includes("application/json")) {
      try {
        const parsed: unknown = JSON.parse(raw);

        if (isPublicApiErrorResponse(parsed)) {
          throw new PublicApiError({
            httpStatus: response.status,
            message: parsed.message,
            apiStatus: parsed.status,
            path: parsed.path,
            timestamp: parsed.timestamp,
            raw,
          });
        }
      } catch (error) {
        if (error instanceof PublicApiError) {
          throw error;
        }
      }
    }

    throw new PublicApiError({
      httpStatus: response.status,
      message: response.statusText || "API request failed",
      apiStatus: null,
      path,
      timestamp: null,
      raw,
    });
  }

  return response.json() as Promise<T>;
}

// ─── Public endpoint methods ──────────────────────────────────────────────────

/**
 * GET /api/area/national/{nationalGeoId}
 *
 * Story 7.2 uses the migration-seeded national row for the default summary panel.
 */
export function getNationalArea(): Promise<AreaResponse> {
  return apiGet<AreaResponse>(`/api/area/national/${NATIONAL_GEO_ID}`);
}

/**
 * GET /api/area/{geoLevel}/{identifier}
 */
export function getArea(
  geoLevel: AreaDetailGeoLevel,
  identifier: string
): Promise<AreaResponse> {
  return apiGet<AreaResponse>(`/api/area/${geoLevel}/${identifier}`);
}

/**
 * GET /api/area/{geoLevel}/{identifier}/history?category=...&periods=...
 */
export function getAreaHistory(
  geoLevel: AreaDetailGeoLevel,
  identifier: string,
  category: string,
  periods = 12
): Promise<HistoryResponse> {
  const params = new URLSearchParams({
    category,
    periods: String(periods),
  });

  return apiGet<HistoryResponse>(
    `/api/area/${geoLevel}/${identifier}/history?${params.toString()}`
  );
}

/**
 * GET /api/area/{geoLevel}/{identifier}/children
 */
export function getAreaChildren(
  geoLevel: AreaDetailGeoLevel,
  identifier: string
): Promise<ChildrenResponse> {
  return apiGet<ChildrenResponse>(
    `/api/area/${geoLevel}/${identifier}/children`
  );
}

/**
 * GET /api/area/search?q=...&level=...
 */
export function searchAreas(
  query: string,
  level: GeoLevel
): Promise<AreaSearchResponse> {
  const params = new URLSearchParams({
    q: query,
    level,
  });

  return apiGet<AreaSearchResponse>(`/api/area/search?${params.toString()}`);
}

/**
 * GET /api/map/tiles/{geoLevel}?bbox=...
 */
export function getMapTiles(
  geoLevel: MapTileGeoLevel,
  bbox: string
): Promise<GeoJsonFeatureCollection> {
  const params = new URLSearchParams({ bbox });
  return apiGet<GeoJsonFeatureCollection>(
    `/api/map/tiles/${geoLevel}?${params.toString()}`
  );
}
