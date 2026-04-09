// public-web/lib/state-map.ts
import type { Feature, Geometry } from "geojson";
import { feature } from "topojson-client";
import type { GeometryCollection, Topology } from "topojson-specification";
import type { GeoJsonFeatureCollection } from "@/lib/api";

export const NATIONAL_STATE_BBOX = "-180,-90,180,90";
const STATE_BOUNDARY_ASSET = "/geo/us-states-10m.json";

type StateFeatureProperties = { name?: string };
type StatesTopology = Topology<{
  states: GeometryCollection<StateFeatureProperties>;
}>;

export interface StateBoundaryFeature
  extends Feature<Geometry, StateFeatureProperties> {
  id?: string | number;
}

export interface StateMapRegion {
  fips: string;
  name: string;
  displayLabel: string;
  geometry: Geometry;
  hasTile: boolean;
}

export async function loadStateBoundaryFeatures(): Promise<
  StateBoundaryFeature[]
> {
  const response = await fetch(STATE_BOUNDARY_ASSET, {
    cache: "force-cache",
  });

  if (!response.ok) {
    throw new Error("Failed to load state boundary asset");
  }

  const topology = (await response.json()) as StatesTopology;
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
    };
  });
}
