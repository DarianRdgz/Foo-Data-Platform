export const BOUNDARY_ASSETS = {
  states: "/geo/us-states-10m.json",
  counties: "/geo/us-counties-10m.json",
} as const;

export type BoundaryAssetKey = keyof typeof BOUNDARY_ASSETS;

export function getBoundaryAssetPath(key: BoundaryAssetKey) {
  return BOUNDARY_ASSETS[key];
}
