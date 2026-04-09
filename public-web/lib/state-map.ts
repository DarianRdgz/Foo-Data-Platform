// public-web/lib/state-map.ts
import { geoBounds } from "d3-geo";
import type { Feature, Geometry } from "geojson";
import { feature } from "topojson-client";
import type { GeometryCollection, Topology } from "topojson-specification";
import type { GeoJsonFeatureCollection } from "@/lib/api";

export const NATIONAL_STATE_BBOX = "-180,-90,180,90";

const STATE_BOUNDARY_ASSET = "/geo/us-states-10m.json";
const COUNTY_BOUNDARY_ASSET = "/geo/us-counties-10m.json";

const TILE_CACHE_PREFIX = "fdp:map-tiles:";
const STATE_DRILLDOWN_BBOX_OVERRIDES: Record<string, string> = {
  "02": "-179.9000,51.2000,-129.9000,71.6000",
};

type StateFeatureProperties = { name?: string };
type CountyFeatureProperties = { name?: string };

type StatesTopology = Topology<{
  states: GeometryCollection<StateFeatureProperties>;
}>;

type CountiesTopology = Topology<{
  counties: GeometryCollection<CountyFeatureProperties>;
}>;

let stateBoundaryPromise: Promise<StateBoundaryFeature[]> | null = null;
let countyBoundaryPromise: Promise<CountyBoundaryFeature[]> | null = null;

export interface StateBoundaryFeature
  extends Feature<Geometry, StateFeatureProperties> {
  id?: string | number;
}

export interface CountyBoundaryFeature
  extends Feature<Geometry, CountyFeatureProperties> {
  id?: string | number;
}

export interface StateMapRegion {
  fips: string;
  name: string;
  displayLabel: string;
  geometry: Geometry;
  hasTile: boolean;
  categoryCount: number;
  riskTier: string | null;
  riskScore: number | null;
}

export interface CountyMapRegion {
  fips: string;
  stateFips: string;
  name: string;
  displayLabel: string;
  geometry: Geometry;
  hasTile: boolean;
  cbsaCode: string | null;
  categoryCount: number;
  riskTier: string | null;
  riskScore: number | null;
}

export interface MetroMapRegion {
  cbsaCode: string;
  name: string;
  displayLabel: string;
  centroidLatitude: number | null;
  centroidLongitude: number | null;
  categoryCount: number;
  riskTier: string | null;
  riskScore: number | null;
}

function toFiniteNumber(value: unknown): number | null {
  if (typeof value === "number" && Number.isFinite(value)) {
    return value;
  }

  if (typeof value === "string") {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : null;
  }

  return null;
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value));
}

function getTileCacheKey(geoLevel: string, bbox: string): string {
  return `${TILE_CACHE_PREFIX}${geoLevel}:${bbox}`;
}

export function readCachedTileCollection(
  geoLevel: string,
  bbox: string
): GeoJsonFeatureCollection | null {
  if (typeof window === "undefined") {
    return null;
  }

  try {
    const raw = window.sessionStorage.getItem(getTileCacheKey(geoLevel, bbox));
    if (!raw) {
      return null;
    }

    const parsed = JSON.parse(raw) as GeoJsonFeatureCollection;

    if (
      parsed &&
      parsed.type === "FeatureCollection" &&
      Array.isArray(parsed.features)
    ) {
      return parsed;
    }
  } catch {
    // ignore malformed cache
  }

  return null;
}

export function writeCachedTileCollection(
  geoLevel: string,
  bbox: string,
  value: GeoJsonFeatureCollection
): void {
  if (typeof window === "undefined") {
    return;
  }

  try {
    window.sessionStorage.setItem(
      getTileCacheKey(geoLevel, bbox),
      JSON.stringify(value)
    );
  } catch {
    // ignore storage errors
  }
}

export async function loadStateBoundaryFeatures(): Promise<
  StateBoundaryFeature[]
> {
  if (!stateBoundaryPromise) {
    stateBoundaryPromise = fetch(STATE_BOUNDARY_ASSET, {
      cache: "force-cache",
    })
      .then((response) => {
        if (!response.ok) {
          throw new Error("Failed to load state boundary asset");
        }
        return response.json();
      })
      .then((json) => {
        const topology = json as StatesTopology;
        const collection = feature<StateFeatureProperties>(
          topology,
          topology.objects.states
        );

        return collection.features.map((item) => ({
          ...item,
          id:
            typeof item.id === "number"
              ? String(item.id).padStart(2, "0")
              : String(item.id ?? ""),
        }));
      });
  }

  return stateBoundaryPromise;
}

export async function loadCountyBoundaryFeatures(): Promise<
  CountyBoundaryFeature[]
> {
  if (!countyBoundaryPromise) {
    countyBoundaryPromise = fetch(COUNTY_BOUNDARY_ASSET, {
      cache: "force-cache",
    })
      .then((response) => {
        if (!response.ok) {
          throw new Error("Failed to load county boundary asset");
        }
        return response.json();
      })
      .then((json) => {
        const topology = json as CountiesTopology;
        const collection = feature<CountyFeatureProperties>(
          topology,
          topology.objects.counties
        );

        return collection.features.map((item) => ({
          ...item,
          id:
            typeof item.id === "number"
              ? String(item.id).padStart(5, "0")
              : String(item.id ?? ""),
        }));
      });
  }

  return countyBoundaryPromise;
}

export function mergeStateRegions(
  boundaries: StateBoundaryFeature[],
  tiles: GeoJsonFeatureCollection
): StateMapRegion[] {
  const tileByBoundaryKey = new Map(
    tiles.features
      .filter((item) => item.properties.boundaryKind === "state")
      .map((item) => [String(item.properties.boundaryKey), item.properties] as const)
  );

  return boundaries.map((boundary) => {
    const fips = String(boundary.id ?? "").padStart(2, "0");
    const tile = tileByBoundaryKey.get(fips);

    return {
      fips,
      name: boundary.properties?.name ?? fips,
      displayLabel:
        typeof tile?.displayLabel === "string"
          ? tile.displayLabel
          : boundary.properties?.name ?? fips,
      geometry: boundary.geometry,
      hasTile: Boolean(tile),
      categoryCount: toFiniteNumber(tile?.categoryCount) ?? 0,
      riskTier: typeof tile?.riskTier === "string" ? tile.riskTier : null,
      riskScore: toFiniteNumber(tile?.riskScore),
    };
  });
}

export function findStateBoundary(
  boundaries: StateBoundaryFeature[],
  stateFips: string
): StateBoundaryFeature | null {
  return (
    boundaries.find(
      (boundary) => String(boundary.id ?? "").padStart(2, "0") === stateFips
    ) ?? null
  );
}

export function buildStateDrilldownBbox(
  stateFips: string,
  geometry: Geometry,
  paddingDegrees = 0.35
): string {
  const override = STATE_DRILLDOWN_BBOX_OVERRIDES[stateFips];
  if (override) {
    return override;
  }

  const [[west, south], [east, north]] = geoBounds({
    type: "Feature",
    geometry,
    properties: {},
  } as Feature<Geometry>);

  const paddedWest = clamp(west - paddingDegrees, -180, 180);
  const paddedSouth = clamp(south - paddingDegrees, -90, 90);
  const paddedEast = clamp(east + paddingDegrees, -180, 180);
  const paddedNorth = clamp(north + paddingDegrees, -90, 90);

  if (paddedWest >= paddedEast) {
    throw new Error(`Could not derive a valid drilldown bbox for state ${stateFips}`);
  }

  return [
    paddedWest.toFixed(4),
    paddedSouth.toFixed(4),
    paddedEast.toFixed(4),
    paddedNorth.toFixed(4),
  ].join(",");
}

export function mergeCountyRegions(
  boundaries: CountyBoundaryFeature[],
  tiles: GeoJsonFeatureCollection,
  selectedStateFips: string
): CountyMapRegion[] {
  const tileByBoundaryKey = new Map(
    tiles.features
      .filter(
        (item) =>
          item.properties.boundaryKind === "county" &&
          String(item.properties.boundaryKey).startsWith(selectedStateFips)
      )
      .map((item) => [String(item.properties.boundaryKey), item.properties] as const)
  );

  return boundaries
    .filter((boundary) =>
      String(boundary.id ?? "").padStart(5, "0").startsWith(selectedStateFips)
    )
    .map((boundary) => {
      const fips = String(boundary.id ?? "").padStart(5, "0");
      const tile = tileByBoundaryKey.get(fips);
      const fallbackName =
        boundary.properties?.name ??
        (typeof tile?.name === "string" ? tile.name : fips);

      return {
        fips,
        stateFips: fips.slice(0, 2),
        name: fallbackName,
        displayLabel:
          typeof tile?.displayLabel === "string"
            ? tile.displayLabel
            : `${fallbackName} County`,
        geometry: boundary.geometry,
        hasTile: Boolean(tile),
        cbsaCode:
          typeof tile?.cbsaCode === "string" ? tile.cbsaCode : null,
        categoryCount: toFiniteNumber(tile?.categoryCount) ?? 0,
        riskTier:
          typeof tile?.riskTier === "string" ? tile.riskTier : null,
        riskScore: toFiniteNumber(tile?.riskScore),
      };
    });
}

export function extractMetroRegions(
  tiles: GeoJsonFeatureCollection
): MetroMapRegion[] {
  const deduped = new Map<string, MetroMapRegion>();

  for (const feature of tiles.features) {
    if (feature.properties.boundaryKind !== "metro") {
      continue;
    }

    const cbsaCode =
      typeof feature.properties.cbsaCode === "string"
        ? feature.properties.cbsaCode
        : String(feature.properties.boundaryKey ?? "");

    if (!cbsaCode) {
      continue;
    }

    deduped.set(cbsaCode, {
      cbsaCode,
      name:
        typeof feature.properties.name === "string"
          ? feature.properties.name
          : cbsaCode,
      displayLabel:
        typeof feature.properties.displayLabel === "string"
          ? feature.properties.displayLabel
          : cbsaCode,
      centroidLatitude: toFiniteNumber(feature.properties.centroidLatitude),
      centroidLongitude: toFiniteNumber(feature.properties.centroidLongitude),
      categoryCount: toFiniteNumber(feature.properties.categoryCount) ?? 0,
      riskTier:
        typeof feature.properties.riskTier === "string"
          ? feature.properties.riskTier
          : null,
      riskScore: toFiniteNumber(feature.properties.riskScore),
    });
  }

  return Array.from(deduped.values()).sort((a, b) =>
    a.displayLabel.localeCompare(b.displayLabel)
  );
}