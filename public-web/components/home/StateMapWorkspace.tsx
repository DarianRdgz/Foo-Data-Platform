// public-web/components/home/StateMapWorkspace.tsx
"use client";

import { useEffect, useMemo, useState } from "react";
import { geoAlbersUsa, geoPath } from "d3-geo";
import { getMapTiles } from "@/lib/api";
import type { HomeBrowseLevel, HomeTab } from "@/lib/home-query";
import {
  loadStateBoundaryFeatures,
  mergeStateRegions,
  NATIONAL_STATE_BBOX,
  type StateMapRegion,
} from "@/lib/state-map";

interface Props {
  tab: HomeTab;
  selectedBrowseLevel: HomeBrowseLevel;
  selectedStateFips: string | null;
  compareIds: string[];
  onBrowseSelect: (stateFips: string) => void;
  onCompareToggle: (stateFips: string) => void;
}

interface RenderRegion extends StateMapRegion {
  pathD: string;
}

export default function StateMapWorkspace({
  tab,
  selectedBrowseLevel,
  selectedStateFips,
  compareIds,
  onBrowseSelect,
  onCompareToggle,
}: Props) {
  const [regions, setRegions] = useState<StateMapRegion[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function load() {
      setLoading(true);
      setError(null);

      try {
        const [boundaries, tiles] = await Promise.all([
          loadStateBoundaryFeatures(),
          getMapTiles("state", NATIONAL_STATE_BBOX),
        ]);

        if (cancelled) {
          return;
        }

        setRegions(mergeStateRegions(boundaries, tiles));
      } catch (cause) {
        if (!cancelled) {
          setError(
            cause instanceof Error
              ? cause.message
              : "Failed to load state map workspace."
          );
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    }

    load();

    return () => {
      cancelled = true;
    };
  }, []);

  const renderedRegions = useMemo<RenderRegion[]>(() => {
    if (regions.length === 0) {
      return [];
    }

    const featureCollection = {
      type: "FeatureCollection",
      features: regions.map((region) => ({
        type: "Feature",
        id: region.fips,
        properties: { name: region.name },
        geometry: region.geometry,
      })),
    } as const;

    const projection = geoAlbersUsa().fitSize([960, 600], featureCollection as never);
    const pathBuilder = geoPath(projection);

    return regions
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
  }, [regions]);

  function handleActivate(stateFips: string) {
    if (tab === "compare") {
      onCompareToggle(stateFips);
      return;
    }

    onBrowseSelect(stateFips);
  }

  return (
    <section className="map-card" aria-labelledby="homepage-map-title">
      <div className="map-card-header">
        <div>
          <p className="map-kicker">National map workspace</p>
          <h2 id="homepage-map-title">State map</h2>
        </div>

        <div className="map-badges">
          <span className="map-badge">tab: {tab}</span>
          <span className="map-badge">level: {selectedBrowseLevel}</span>
        </div>
      </div>

      {selectedBrowseLevel !== "state" ? (
        <p className="map-note">
          Story 7.2 still renders a state-level map. The selected browse level is
          preserved in homepage state and query params for later stories.
        </p>
      ) : null}

      {loading ? (
        <div className="map-placeholder">Loading state map…</div>
      ) : error ? (
        <div className="map-placeholder map-error">{error}</div>
      ) : (
        <div className="map-svg-wrap">
          <svg
            className="map-svg"
            viewBox="0 0 960 600"
            role="img"
            aria-label="United States state map"
          >
            {renderedRegions.map((region) => {
              const isBrowseSelected = selectedStateFips === region.fips;
              const isCompareSelected = compareIds.includes(region.fips);

              const className = [
                "state-shape",
                isBrowseSelected ? "state-shape-selected" : "",
                isCompareSelected ? "state-shape-compare" : "",
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
                  onClick={() => handleActivate(region.fips)}
                  onKeyDown={(event) => {
                    if (event.key === "Enter" || event.key === " ") {
                      event.preventDefault();
                      handleActivate(region.fips);
                    }
                  }}
                >
                  <title>{region.displayLabel}</title>
                </path>
              );
            })}
          </svg>
        </div>
      )}

      <div className="map-legend">
        <span className="legend-item">
          <span className="legend-swatch legend-swatch-default" />
          Available state
        </span>
        <span className="legend-item">
          <span className="legend-swatch legend-swatch-selected" />
          Browse selection
        </span>
        <span className="legend-item">
          <span className="legend-swatch legend-swatch-compare" />
          Compare selection
        </span>
      </div>
    </section>
  );
}