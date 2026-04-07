import type { GeoLevel } from "@/lib/api";

export type BetaAreaLevel = Extract<GeoLevel, "state" | "county" | "metro" | "zip">;

export function homeRoute() {
  return "/";
}

export function stateRoute(stateCode: string) {
  return `/state/${stateCode}`;
}

export function areaRoute(geoLevel: BetaAreaLevel, identifier: string) {
  return `/area/${geoLevel}/${identifier}`;
}

export function browseRoute() {
  return "/browse";
}

export function browseStateRoute(stateCode: string) {
  return `/browse/${stateCode}`;
}

export function compareRoute(level?: BetaAreaLevel, ids?: string[]) {
  const params = new URLSearchParams();

  if (level) {
    params.set("level", level);
  }

  if (ids && ids.length > 0) {
    params.set("ids", ids.join(","));
  }

  const query = params.toString();
  return query ? `/compare?${query}` : "/compare";
}

export function normalizeIdentifier(value: string) {
  return value.trim();
}
