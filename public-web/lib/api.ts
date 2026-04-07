const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL;

if (!API_BASE_URL) {
  throw new Error("NEXT_PUBLIC_API_BASE_URL is required");
}

export type GeoLevel = "national" | "state" | "metro" | "county" | "city" | "zip";

export interface AreaParent {
  geoId: string;
  geoLevel: string;
  name: string;
  displayLabel: string;
}

export interface AreaSnapshot {
  category: string;
  snapshotPeriod: string;
  source: string;
  isRollup: boolean;
  payload: Record<string, unknown>;
  change: {
    pctChange?: number;
    direction?: string;
    magnitude?: string;
  } | null;
}

export interface AreaResponse {
  geoId: string;
  geoLevel: string;
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
  geoLevel: string;
  category: string;
  periodsRequested: number;
  points: HistoryPoint[];
}

export interface ChildArea {
  geoId: string;
  geoLevel: string;
  name: string;
  displayLabel: string;
  coverageCount: number;
}

export interface ChildrenResponse {
  geoId: string;
  geoLevel: string;
  childGeoLevel: string;
  children: ChildArea[];
}

export interface AreaSearchResult {
  geoId: string;
  geoLevel: string;
  name: string;
  displayLabel: string;
  fipsCode: string | null;
  cbsaCode: string | null;
  zipCode: string | null;
}

export interface AreaSearchResponse {
  query: string;
  level: string;
  results: AreaSearchResult[];
}

export interface GeoJsonFeature {
  type: "Feature";
  geometry: unknown | null;
  properties: Record<string, unknown>;
}

export interface GeoJsonFeatureCollection {
  type: "FeatureCollection";
  features: GeoJsonFeature[];
}

async function apiGet<T>(path: string): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    headers: { Accept: "application/json" },
    cache: "no-store",
  });

  if (!response.ok) {
    const text = await response.text();
    throw new Error(`API request failed: ${response.status} ${response.statusText} - ${text}`);
  }

  return response.json() as Promise<T>;
}

export function getArea(geoLevel: string, identifier: string) {
  return apiGet<AreaResponse>(`/api/area/${geoLevel}/${identifier}`);
}

export function getAreaHistory(geoLevel: string, identifier: string, category: string, periods = 12) {
  const params = new URLSearchParams({ category, periods: String(periods) });
  return apiGet<HistoryResponse>(`/api/area/${geoLevel}/${identifier}/history?${params.toString()}`);
}

export function getAreaChildren(geoLevel: string, identifier: string) {
  return apiGet<ChildrenResponse>(`/api/area/${geoLevel}/${identifier}/children`);
}

export function searchAreas(query: string, level: string) {
  const params = new URLSearchParams({ q: query, level });
  return apiGet<AreaSearchResponse>(`/api/area/search?${params.toString()}`);
}

export function getMapTiles(geoLevel: "state" | "county" | "metro" | "city" | "zip", bbox: string) {
  return apiGet<GeoJsonFeatureCollection>(`/api/map/tiles/${geoLevel}?bbox=${encodeURIComponent(bbox)}`);
}
