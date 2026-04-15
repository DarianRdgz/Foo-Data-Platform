// public-web/components/home/StateMapWorkspace.tsx
"use client";

import { useEffect, useMemo, useState } from "react";
import { geoAlbersUsa, geoPath } from "d3-geo";
import { getMapTiles } from "@/lib/api";
import type { HomeBrowseLevel, HomeTab } from "@/lib/home-query";
import { getStateByCode } from "@/lib/us-states";
import {
  buildStateDrilldownBbox,
  extractMetroRegions,
  findStateBoundary,
  loadCountyBoundaryFeatures,
  loadStateBoundaryFeatures,
  mergeCountyRegions,
  mergeStateRegions,
  NATIONAL_STATE_BBOX,
  readCachedTileCollection,
  writeCachedTileCollection,
  type CountyMapRegion,
  type MetroMapRegion,
  type StateBoundaryFeature,
  type StateMapRegion,
} from "@/lib/state-map";
import type { ComparableGeoLevel } from "@/lib/compare-validation";

interface Props {
  tab: HomeTab;
  selectedBrowseLevel: HomeBrowseLevel;
  selectedStateFips: string | null;
  selectedCountyFips: string | null;
  selectedMetroCbsa: string | null;
  compareIds: string[];
  onBrowseSelectState: (stateFips: string) => void;
  onBrowseSelectCounty: (countyFips: string) => void;
  onBrowseSelectMetro: (cbsaCode: string) => void;
  onBrowseLevelChange: (
    nextLevel: Extract<HomeBrowseLevel, "state" | "county" | "metro">
  ) => void;
  onBackToUnitedStates: () => void;
  onBackToState: () => void;
  onCompareToggle?: (id: string, level: ComparableGeoLevel) => void;
}

interface RenderStateRegion extends StateMapRegion {
  pathD: string;
}

interface RenderCountyRegion extends CountyMapRegion {
  pathD: string;
}

interface RenderMetroMarker extends MetroMapRegion {
  x: number;
  y: number;
  shortLabel: string;
}

function shortenMetroLabel(label: string): string {
  return label
    .replace(/,\s*[A-Z]{2}\s*Metro Area$/i, "")
    .replace(/\s+Metro Area$/i, "")
    .replace(/\s+Micropolitan Area$/i, "");
}

async function getCachedTiles(
  geoLevel: "state" | "county" | "metro",
  bbox: string
) {
  const cached = readCachedTileCollection(geoLevel, bbox);
  if (cached) {
    return cached;
  }

  const fresh = await getMapTiles(geoLevel, bbox);
  writeCachedTileCollection(geoLevel, bbox, fresh);
  return fresh;
}

export default function StateMapWorkspace({
  tab,
  selectedBrowseLevel,
  selectedStateFips,
  selectedCountyFips,
  selectedMetroCbsa,
  compareIds,
  onBrowseSelectState,
  onBrowseSelectCounty,
  onBrowseSelectMetro,
  onBrowseLevelChange,
  onBackToUnitedStates,
  onBackToState,
  onCompareToggle,
}: Props) {
  const [stateBoundaries, setStateBoundaries] = useState<StateBoundaryFeature[]>(
    []
  );
  const [stateRegions, setStateRegions] = useState<StateMapRegion[]>([]);
  const [countyCache, setCountyCache] = useState<Record<string, CountyMapRegion[]>>(
    {}
  );
  const [metroCache, setMetroCache] = useState<Record<string, MetroMapRegion[]>>(
    {}
  );

  const [nationalLoading, setNationalLoading] = useState(true);
  const [countyLoading, setCountyLoading] = useState(false);
  const [metroLoading, setMetroLoading] = useState(false);
  const [nationalError, setNationalError] = useState<string | null>(null);
  const [drilldownError, setDrilldownError] = useState<string | null>(null);

  const drilldownLoading = countyLoading || metroLoading;

  const stateName = selectedStateFips
    ? getStateByCode(selectedStateFips)?.name ?? selectedStateFips
    : null;

  const showNationalMap =
    !selectedStateFips || selectedBrowseLevel === "state";

  useEffect(() => {
    let cancelled = false;

    async function loadNationalStateMap() {
      setNationalLoading(true);
      setNationalError(null);

      try {
        const [boundaries, tiles] = await Promise.all([
          loadStateBoundaryFeatures(),
          getCachedTiles("state", NATIONAL_STATE_BBOX),
        ]);

        if (cancelled) {
          return;
        }

        setStateBoundaries(boundaries);
        setStateRegions(mergeStateRegions(boundaries, tiles));
      } catch (cause) {
        if (!cancelled) {
          setNationalError(
            cause instanceof Error
              ? cause.message
              : "Failed to load national state map."
          );
        }
      } finally {
        if (!cancelled) {
          setNationalLoading(false);
        }
      }
    }

    loadNationalStateMap();

    return () => {
      cancelled = true;
    };
  }, []);

  const selectedStateBoundary = useMemo(() => {
    if (!selectedStateFips) {
      return null;
    }

    return findStateBoundary(stateBoundaries, selectedStateFips);
  }, [selectedStateFips, stateBoundaries]);

  useEffect(() => {
    let cancelled = false;

    async function loadCountyDrilldown() {
      if (
        !selectedStateFips ||
        !selectedStateBoundary ||
        !["county", "metro"].includes(selectedBrowseLevel)
      ) {
        return;
      }

      if (countyCache[selectedStateFips]) {
        setDrilldownError(null);
        return;
      }

      setCountyLoading(true);
      setDrilldownError(null);

      try {
        const bbox = buildStateDrilldownBbox(
          selectedStateFips,
          selectedStateBoundary.geometry
        );

        const [countyBoundaries, countyTiles] = await Promise.all([
          loadCountyBoundaryFeatures(),
          getCachedTiles("county", bbox),
        ]);

        if (cancelled) {
          return;
        }

        setCountyCache((prev) => ({
          ...prev,
          [selectedStateFips]: mergeCountyRegions(
            countyBoundaries,
            countyTiles,
            selectedStateFips
          ),
        }));
      } catch (cause) {
        if (!cancelled) {
          setDrilldownError(
            cause instanceof Error
              ? cause.message
              : "Failed to load county drilldown."
          );
        }
      } finally {
        if (!cancelled) {
          setCountyLoading(false);
        }
      }
    }

    loadCountyDrilldown();

    return () => {
      cancelled = true;
    };
  }, [
    selectedBrowseLevel,
    selectedStateBoundary,
    selectedStateFips,
    countyCache,
  ]);

  useEffect(() => {
    let cancelled = false;

    async function loadMetroOverlay() {
      if (
        !selectedStateFips ||
        !selectedStateBoundary ||
        selectedBrowseLevel !== "metro"
      ) {
        return;
      }

      if (metroCache[selectedStateFips]) {
        return;
      }

      setMetroLoading(true);
      setDrilldownError(null);

      try {
        const bbox = buildStateDrilldownBbox(
          selectedStateFips,
          selectedStateBoundary.geometry
        );

        const metroTiles = await getCachedTiles("metro", bbox);

        if (cancelled) {
          return;
        }

        setMetroCache((prev) => ({
          ...prev,
          [selectedStateFips]: extractMetroRegions(metroTiles),
        }));
      } catch (cause) {
        if (!cancelled) {
          setDrilldownError(
            cause instanceof Error
              ? cause.message
              : "Failed to load metro overlay."
          );
        }
      } finally {
        if (!cancelled) {
          setMetroLoading(false);
        }
      }
    }

    loadMetroOverlay();

    return () => {
      cancelled = true;
    };
  }, [
    selectedBrowseLevel,
    selectedStateBoundary,
    selectedStateFips,
    metroCache,
  ]);

  const renderedStateRegions = useMemo<RenderStateRegion[]>(() => {
    if (stateRegions.length === 0) {
      return [];
    }

    const featureCollection = {
      type: "FeatureCollection",
      features: stateRegions.map((region) => ({
        type: "Feature",
        id: region.fips,
        properties: { name: region.name },
        geometry: region.geometry,
      })),
    } as const;

    const projection = geoAlbersUsa().fitSize([960, 600], featureCollection as never);
    const pathBuilder = geoPath(projection);

    return stateRegions
      .map((region) => {
        const pathD =
          pathBuilder({
            type: "Feature",
            properties: { name: region.name },
            geometry: region.geometry,
          } as never) ?? "";

        return {
          ...region,
          pathD,
        };
      })
      .filter((region) => region.pathD.length > 0);
  }, [stateRegions]);

  const countyProjection = useMemo(() => {
    if (!selectedStateBoundary || !selectedStateFips) {
      return null;
    }

    const countyRegions = countyCache[selectedStateFips] ?? [];
    if (countyRegions.length === 0) {
      return null;
    }

    const featureCollection = {
      type: "FeatureCollection",
      features: countyRegions.map((region) => ({
        type: "Feature",
        id: region.fips,
        properties: { name: region.name },
        geometry: region.geometry,
      })),
    } as const;

    return geoAlbersUsa().fitSize([960, 600], featureCollection as never);
  }, [selectedStateBoundary, selectedStateFips, countyCache]);

  const renderedCountyView = useMemo(() => {
    if (!selectedStateBoundary || !selectedStateFips) {
      return null;
    }

    const countyRegions = countyCache[selectedStateFips] ?? [];
    const metroRegions = metroCache[selectedStateFips] ?? [];

    if (countyRegions.length === 0) {
      return null;
    }

    const featureCollection = {
      type: "FeatureCollection",
      features: countyRegions.map((region) => ({
        type: "Feature",
        id: region.fips,
        properties: { name: region.name },
        geometry: region.geometry,
      })),
    } as const;

    if (!countyProjection) {
      return null;
    }

    const projection = countyProjection;
    const pathBuilder = geoPath(projection);

    const counties: RenderCountyRegion[] = countyRegions
      .map((region) => {
        const pathD =
          pathBuilder({
            type: "Feature",
            properties: { name: region.name },
            geometry: region.geometry,
          } as never) ?? "";

        return {
          ...region,
          pathD,
        };
      })
      .filter((region) => region.pathD.length > 0);

    const metroMarkers: RenderMetroMarker[] =
      selectedBrowseLevel === "metro"
        ? metroRegions
            .map((metro) => {
              if (
                metro.centroidLongitude === null ||
                metro.centroidLatitude === null
              ) {
                return null;
              }

              const point = projection([
                metro.centroidLongitude,
                metro.centroidLatitude,
              ]);

              if (!point) {
                return null;
              }

              return {
                ...metro,
                x: point[0],
                y: point[1],
                shortLabel: shortenMetroLabel(metro.displayLabel),
              };
            })
            .filter((value): value is RenderMetroMarker => value !== null)
        : [];

    return {
      counties,
      metroMarkers,
    };
  }, [
      selectedBrowseLevel,
      selectedStateBoundary,
      selectedStateFips,
      countyCache,
      metroCache,
      countyProjection,
    ]);

  function handleStateActivate(stateFips: string) {
    if (tab === "compare") {
      onCompareToggle?.(stateFips, "state");
      return;
    }

    onBrowseSelectState(stateFips);
  }

  function handleCountyActivate(countyFips: string) {
    if (tab === "compare") {
      onCompareToggle?.(countyFips, "county");
      return;
    }

    onBrowseSelectCounty(countyFips);
  }

  function handleMetroActivate(cbsaCode: string) {
    if (tab === "compare") {
      onCompareToggle?.(cbsaCode, "metro");
      return;
    }

    onBrowseSelectMetro(cbsaCode);
  }

  return (
    <section className="map-card" aria-labelledby="homepage-map-title">
      <div className="map-card-header">
        <div>
          <p className="map-kicker">
            {showNationalMap ? "National map workspace" : "Selected state workspace"}
          </p>
          <h2 id="homepage-map-title">
            {showNationalMap
              ? "State map"
              : `${stateName ?? selectedStateFips} · ${
                  selectedBrowseLevel === "metro" ? "Metro overlay" : "County view"
                }`}
          </h2>
        </div>

        <div className="map-badges">
          <span className="map-badge">tab: {tab}</span>
          <span className="map-badge">
            level: {showNationalMap ? "state" : selectedBrowseLevel}
          </span>
        </div>
      </div>

      {!showNationalMap ? (
        <div className="map-context-actions">
          <button
            type="button"
            className="btn-secondary"
            onClick={onBackToUnitedStates}
          >
            Back to U.S.
          </button>

          <div
            className="map-level-toggle"
            role="group"
            aria-label="Selected state browse mode"
          >
            <button
              type="button"
              className={`map-level-button ${
                selectedBrowseLevel === "county" ? "map-level-button-active" : ""
              }`}
              onClick={() => onBrowseLevelChange("county")}
            >
              Counties
            </button>

            <button
              type="button"
              className={`map-level-button ${
                selectedBrowseLevel === "metro" ? "map-level-button-active" : ""
              }`}
              onClick={() => onBrowseLevelChange("metro")}
            >
              Metro overlay
            </button>
          </div>

          {(selectedCountyFips || selectedMetroCbsa) && (
            <button
              type="button"
              className="btn-secondary"
              onClick={onBackToState}
            >
              Back to State
            </button>
          )}
        </div>
      ) : null}

      {showNationalMap ? (
        <p className="map-note">
          {tab === "compare"
            ? "Compare mode is active. Click states to add or remove up to four selections."
            : selectedStateFips
              ? "A state is selected. The summary panel has updated, and you can use the pop-up to enter county view."
              : "Single-click a state to preview it and update the summary panel without leaving the homepage."}
        </p>
      ) : tab === "compare" && selectedBrowseLevel === "metro" ? (
        <p className="map-note">
          Compare mode is active. Click metro markers or metro chips to add or remove up to four metro selections.
        </p>
      ) : tab === "compare" ? (
        <p className="map-note">
          Compare mode is active. Click counties to add or remove up to four selections.
        </p>
      ) : selectedBrowseLevel === "metro" ? (
        <p className="map-note">
          Metro mode keeps the county map visible. Metro choices appear as overlay markers and matching counties are highlighted when a metro is selected.
        </p>
      ) : (
        <p className="map-note">
          County view is the selected-state drilldown. Click a county to update the summary panel, or switch to metro overlay for a broader regional view.
        </p>
      )}

      {showNationalMap ? (
        nationalLoading ? (
          <div className="map-placeholder">Loading state map…</div>
        ) : nationalError ? (
          <div className="map-placeholder map-error">{nationalError}</div>
        ) : (
          <div className="map-svg-wrap">
            <svg
              className="map-svg"
              viewBox="0 0 960 600"
              role="img"
              aria-label="United States state map"
            >
              {renderedStateRegions.map((region) => {
                const isBrowseSelected = selectedStateFips === region.fips;
                const isCompareSelected = compareIds.includes(region.fips);

                const className = [
                  "state-shape",
                  isBrowseSelected ? "state-shape-selected" : "",
                  isCompareSelected ? "state-shape-compare" : "",
                  !region.hasTile ? "state-shape-no-data" : "",
                ]
                  .filter(Boolean)
                  .join(" ");

                return (
                  <path
                    key={region.fips}
                    d={region.pathD}
                    className={className}
                    tabIndex={0}
                    role="button"
                    onClick={() => handleStateActivate(region.fips)}
                    onKeyDown={(event) => {
                      if (event.key === "Enter" || event.key === " ") {
                        event.preventDefault();
                        handleStateActivate(region.fips);
                      }
                    }}
                  >
                    <title>{region.displayLabel}</title>
                  </path>
                );
              })}
            </svg>
          </div>
        )
      ) : drilldownLoading && !renderedCountyView ? (
        <div className="map-placeholder">Loading selected-state data…</div>
      ) : renderedCountyView ? (
        <>
          {drilldownError ? (
            <div className="map-inline-error">
              {drilldownError}
            </div>
          ) : null}

          <div className="map-svg-wrap">
            <svg
              className="map-svg"
              viewBox="0 0 960 600"
              role="img"
              aria-label={`${stateName ?? selectedStateFips} county map`}
            >
              {renderedCountyView.counties.map((region) => {
                const isCountySelected =
                  tab === "browse" &&
                  selectedBrowseLevel === "county" &&
                  selectedCountyFips === region.fips;

                const isCountyCompareSelected =
                  tab === "compare" && compareIds.includes(region.fips);

                const isMetroMatch =
                  tab === "browse" &&
                  selectedBrowseLevel === "metro" &&
                  selectedMetroCbsa !== null &&
                  region.cbsaCode === selectedMetroCbsa;

                const className = [
                  "county-shape",
                  !region.hasTile ? "county-shape-no-data" : "",
                  isCountySelected || isCountyCompareSelected ? "county-shape-selected" : "",
                  tab === "browse" && selectedBrowseLevel === "metro" && selectedMetroCbsa
                    ? isMetroMatch
                      ? "county-shape-metro-match"
                      : "county-shape-dim"
                    : "",
                ]
                  .filter(Boolean)
                  .join(" ");

                return (
                  <path
                    key={region.fips}
                    d={region.pathD}
                    className={className}
                    tabIndex={selectedBrowseLevel === "county" ? 0 : -1}
                    role={selectedBrowseLevel === "county" ? "button" : undefined}
                    onClick={() => {
                      if (selectedBrowseLevel === "county") {
                        handleCountyActivate(region.fips);
                      }
                    }}
                    onKeyDown={(event) => {
                      if (
                        selectedBrowseLevel === "county" &&
                        (event.key === "Enter" || event.key === " ")
                      ) {
                        event.preventDefault();
                        handleCountyActivate(region.fips);
                      }
                    }}
                  >
                    <title>{region.displayLabel}</title>
                  </path>
                );
              })}

              {selectedBrowseLevel === "metro"
                ? renderedCountyView.metroMarkers.map((metro) => {
                    const isActive =
                      tab === "compare"
                        ? compareIds.includes(metro.cbsaCode)
                        : metro.cbsaCode === selectedMetroCbsa;

                    return (
                      <g
                        key={metro.cbsaCode}
                        className={`metro-marker ${
                          isActive ? "metro-marker-active" : ""
                        }`}
                        transform={`translate(${metro.x}, ${metro.y})`}
                        tabIndex={0}
                        role="button"
                        onClick={() => handleMetroActivate(metro.cbsaCode)}
                        onKeyDown={(event) => {
                          if (event.key === "Enter" || event.key === " ") {
                            event.preventDefault();
                            handleMetroActivate(metro.cbsaCode);
                          }
                        }}
                      >
                        <circle r="12" />
                        <text y="-16" className="metro-marker-label">
                          {metro.shortLabel}
                        </text>
                        <title>{metro.displayLabel}</title>
                      </g>
                    );
                  })
                : null}
            </svg>
          </div>

          {selectedBrowseLevel === "metro" ? (
            renderedCountyView.metroMarkers.length > 0 ? (
              <div className="metro-strip" aria-label="Metro choices">
                {renderedCountyView.metroMarkers.map((metro) => {
                  const isActive =
                    tab === "compare"
                      ? compareIds.includes(metro.cbsaCode)
                      : selectedMetroCbsa === metro.cbsaCode;

                  return (
                    <button
                      key={metro.cbsaCode}
                      type="button"
                      className={`metro-chip ${isActive ? "metro-chip-active" : ""}`}
                      onClick={() => handleMetroActivate(metro.cbsaCode)}
                    >
                      {metro.shortLabel}
                    </button>
                  );
                })}
              </div>
            ) : (
              <p className="map-empty-note">
                No metro overlay options are available for this state yet.
              </p>
            )
          ) : null}
        </>
      ) : drilldownError ? (
        <div className="map-placeholder map-error-soft">
          <p className="map-error-title">Could not load the selected-state drilldown.</p>
          <p className="map-error-copy">{drilldownError}</p>
          <div className="map-error-actions">
            <button type="button" className="btn-secondary" onClick={onBackToUnitedStates}>
              Back to U.S.
            </button>
            <button type="button" className="btn-secondary" onClick={onBackToState}>
              Back to State
            </button>
          </div>
        </div>
      ) : (
        <div className="map-placeholder">
          No county drilldown data is available for this state yet.
        </div>
      )}

      <div className="map-legend">
        {showNationalMap ? (
          <>
            <span className="legend-item">
              <span className="legend-swatch legend-swatch-default" />
              Available state
            </span>
            <span className="legend-item">
              <span className="legend-swatch legend-swatch-selected" />
              Selected state
            </span>
            <span className="legend-item">
              <span className="legend-swatch legend-swatch-compare" />
              Compare selection
            </span>
            <span className="legend-item">
              <span className="legend-swatch legend-swatch-no-data" />
              No data
            </span>
          </>
        ) : (
          <>
            <span className="legend-item">
              <span className="legend-swatch legend-swatch-county" />
              County
            </span>
            <span className="legend-item">
              <span className="legend-swatch legend-swatch-county-selected" />
              Selected county
            </span>
            <span className="legend-item">
              <span className="legend-swatch legend-swatch-metro" />
              Metro match
            </span>
            <span className="legend-item">
              <span className="legend-swatch legend-swatch-no-data" />
              No data
            </span>
          </>
        )}
      </div>
    </section>
  );
}