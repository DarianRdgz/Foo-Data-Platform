"use client";

import { useCallback, useEffect, useMemo, useReducer, useRef, useState } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { getArea, getNationalArea, type AreaResponse } from "@/lib/api";
import {
  buildHomeUrl,
  clearBrowseStateFocus,
  deriveCompareLevelFromContext,
  detectCompareLevelChange,
  normalizeHomeState,
  parseHomeUrlState,
  selectBrowseState,
  setBrowseLevel,
  switchHomeTab,
  toggleCompareSelection,
  type HomeUrlState,
} from "@/lib/home-query";
import { buildHomeSummary } from "@/lib/home-summary";
import { getStateByCode } from "@/lib/us-states";
import type { ComparableGeoLevel } from "@/lib/compare-validation";
import HomeTabs from "@/components/home/HomeTabs";
import HomeSummaryPanel from "@/components/home/HomeSummaryPanel";
import StateMapWorkspace from "@/components/home/StateMapWorkspace";
import StateSelectionModal from "@/components/home/StateSelectionModal";
import { ComparePanel } from "@/components/compare/ComparePanel";

interface SummaryTarget {
  key: string;
  geoLevel: "national" | "state" | "county" | "metro";
  identifier: string | null;
}

export default function HomeClientShell() {
  const router = useRouter();
  const pathname = usePathname() || "/";
  const searchParams = useSearchParams();

  const parsedUrlState = useMemo(() => {
    return parseHomeUrlState(new URLSearchParams(searchParams.toString()));
  }, [searchParams]);

  const [homeState, setHomeState] = useState<HomeUrlState>(parsedUrlState);
  const [selectedCountyFips, setSelectedCountyFips] = useState<string | null>(null);
  const [selectedMetroCbsa, setSelectedMetroCbsa] = useState<string | null>(null);
  const [isStateModalOpen, setIsStateModalOpen] = useState(false);
  const [levelChangeMessage, setLevelChangeMessage] = useState<string | null>(null);

  const summaryCacheRef = useRef<Record<string, AreaResponse>>({});
  const prevCompareLevelRef = useRef<ComparableGeoLevel>(
    deriveCompareLevelFromContext(
      parsedUrlState.selectedStateFips,
      parsedUrlState.selectedBrowseLevel
    )
  );

  const [, forceSummaryRefresh] = useReducer((n: number) => n + 1, 0);
  const [summaryLoading, setSummaryLoading] = useState(true);
  const [summaryError, setSummaryError] = useState<string | null>(null);

  useEffect(() => {
    setHomeState(parsedUrlState);
  }, [parsedUrlState]);

  useEffect(() => {
    if (!homeState.selectedStateFips) {
      setSelectedCountyFips(null);
      setSelectedMetroCbsa(null);
      setIsStateModalOpen(false);
      return;
    }

    if (homeState.tab !== "browse") {
      setSelectedCountyFips(null);
      setSelectedMetroCbsa(null);
      setIsStateModalOpen(false);
      return;
    }

    if (homeState.selectedBrowseLevel !== "county") {
      setSelectedCountyFips(null);
    }

    if (homeState.selectedBrowseLevel !== "metro") {
      setSelectedMetroCbsa(null);
    }

    if (homeState.selectedBrowseLevel !== "state") {
      setIsStateModalOpen(false);
    }
  }, [
    homeState.selectedStateFips,
    homeState.selectedBrowseLevel,
    homeState.tab,
  ]);

  const applyState = useCallback(
    (updater: (prev: HomeUrlState) => HomeUrlState) => {
      setHomeState((prev) => updater(prev));
    },
    []
  );

  useEffect(() => {
    const nextUrl = buildHomeUrl(homeState, pathname);
    const currentQuery = searchParams.toString();
    const currentUrl = currentQuery ? `${pathname}?${currentQuery}` : pathname;

    if (nextUrl !== currentUrl) {
      router.replace(nextUrl, { scroll: false });
    }
  }, [homeState, pathname, router, searchParams]);

  const compareLevel: ComparableGeoLevel = useMemo(() => {
    return deriveCompareLevelFromContext(
      homeState.selectedStateFips,
      homeState.selectedBrowseLevel
    );
  }, [homeState.selectedStateFips, homeState.selectedBrowseLevel]);

  useEffect(() => {
    const prevLevel = prevCompareLevelRef.current;
    const message = detectCompareLevelChange(prevLevel, compareLevel);

    if (message && homeState.compareIds.length > 0) {
      setLevelChangeMessage(message);
      applyState((prev) => normalizeHomeState({ ...prev, compareIds: [] }));
    }

    prevCompareLevelRef.current = compareLevel;
  }, [applyState, compareLevel, homeState.compareIds.length]);

  const summaryTarget = useMemo<SummaryTarget>(() => {
    if (homeState.tab !== "browse") {
      return {
        key: "national",
        geoLevel: "national",
        identifier: null,
      };
    }

    if (selectedMetroCbsa) {
      return {
        key: `metro:${selectedMetroCbsa}`,
        geoLevel: "metro",
        identifier: selectedMetroCbsa,
      };
    }

    if (selectedCountyFips) {
      return {
        key: `county:${selectedCountyFips}`,
        geoLevel: "county",
        identifier: selectedCountyFips,
      };
    }

    if (homeState.selectedStateFips) {
      return {
        key: `state:${homeState.selectedStateFips}`,
        geoLevel: "state",
        identifier: homeState.selectedStateFips,
      };
    }

    return {
      key: "national",
      geoLevel: "national",
      identifier: null,
    };
  }, [
    homeState.tab,
    homeState.selectedStateFips,
    selectedCountyFips,
    selectedMetroCbsa,
  ]);

  const summaryKey = summaryTarget.key;

  useEffect(() => {
    if (homeState.tab !== "browse") {
      setSummaryLoading(false);
      setSummaryError(null);
      return;
    }

    let cancelled = false;

    async function loadSummary() {
      if (summaryCacheRef.current[summaryKey]) {
        setSummaryLoading(false);
        setSummaryError(null);
        return;
      }

      setSummaryLoading(true);
      setSummaryError(null);

      try {
        const area =
          summaryTarget.geoLevel === "national"
            ? await getNationalArea()
            : await getArea(summaryTarget.geoLevel, String(summaryTarget.identifier));

        if (!cancelled) {
          summaryCacheRef.current[summaryKey] = area;
          forceSummaryRefresh();
        }
      } catch (cause) {
        if (!cancelled) {
          setSummaryError(
            cause instanceof Error
              ? cause.message
              : "Failed to load homepage summary."
          );
        }
      } finally {
        if (!cancelled) {
          setSummaryLoading(false);
        }
      }
    }

    loadSummary();

    return () => {
      cancelled = true;
    };
  }, [homeState.tab, summaryKey, summaryTarget.geoLevel, summaryTarget.identifier]);

  const summaryArea = summaryCacheRef.current[summaryKey] ?? null;
  const summary = useMemo(
    () => (summaryArea ? buildHomeSummary(summaryArea) : null),
    [summaryArea]
  );

  const selectedStateName = homeState.selectedStateFips
    ? getStateByCode(homeState.selectedStateFips)?.name ??
      homeState.selectedStateFips
    : null;

  const compareSelectionLabels = useMemo(() => {
    if (compareLevel === "state") {
      return homeState.compareIds.map((value) => ({
        id: value,
        label: getStateByCode(value)?.name ?? value,
      }));
    }

    return homeState.compareIds.map((value) => ({
      id: value,
      label: value,
    }));
  }, [compareLevel, homeState.compareIds]);

  const focusedAreaHref = useMemo(() => {
    if (summaryTarget.geoLevel === "state" && summaryTarget.identifier) {
      return `/area/state/${summaryTarget.identifier}`;
    }

    if (summaryTarget.geoLevel === "county" && summaryTarget.identifier) {
      return `/area/county/${summaryTarget.identifier}`;
    }

    if (summaryTarget.geoLevel === "metro" && summaryTarget.identifier) {
      return `/area/metro/${summaryTarget.identifier}`;
    }

    return null;
  }, [summaryTarget]);

  return (
    <div className="home-shell">
      <section className="home-intro">
        <div>
          <p className="home-kicker">aboutmyarea.net · Homepage map</p>
          <h1>Explore the country, then drill into a state without leaving the homepage</h1>
          <p className="home-lead">
            Click a state to preview it, open county view only when you are ready,
            and switch to metro overlay for a broader regional view.
          </p>
        </div>
      </section>

      <HomeTabs
        currentTab={homeState.tab}
        onTabChange={(nextTab) => {
          setSelectedCountyFips(null);
          setSelectedMetroCbsa(null);
          setIsStateModalOpen(false);
          setLevelChangeMessage(null);

          applyState((prev) => switchHomeTab(prev, nextTab));
        }}
      />

      <section className="home-toolbar">
        <div className="home-toolbar-group">
          <span className="toolbar-label">Context</span>

          <div className="selection-strip">
            <span className="selection-chip">
              {selectedStateName ?? "United States"}
            </span>

            {homeState.tab === "browse" && homeState.selectedStateFips ? (
              <span className="selection-chip selection-chip-muted">
                {homeState.selectedBrowseLevel === "metro"
                  ? "metro overlay"
                  : homeState.selectedBrowseLevel === "county"
                    ? "county view"
                    : "state summary"}
              </span>
            ) : null}
          </div>
        </div>

        <div className="home-toolbar-group">
          {homeState.tab === "compare" ? (
            <>
              <span className="toolbar-label">Compare selection</span>

              <div className="selection-strip">
                {compareSelectionLabels.length > 0 ? (
                  compareSelectionLabels.map((entry) => (
                    <span key={entry.id} className="selection-chip">
                      {entry.label}
                    </span>
                  ))
                ) : (
                  <span className="selection-chip">No areas selected yet</span>
                )}

                {compareSelectionLabels.length > 0 ? (
                  <button
                    type="button"
                    className="text-link-button"
                    onClick={() => {
                      applyState((prev) =>
                        normalizeHomeState({ ...prev, compareIds: [] })
                      );
                    }}
                  >
                    Clear all
                  </button>
                ) : null}
              </div>
            </>
          ) : (
            <>
              <span className="toolbar-label">Browse flow</span>
              <p className="toolbar-help">
                State click updates the summary panel first. County drilldown begins only
                after the explicit county-view CTA.
              </p>
            </>
          )}
        </div>
      </section>

      <StateMapWorkspace
        tab={homeState.tab}
        selectedBrowseLevel={homeState.selectedBrowseLevel}
        selectedStateFips={homeState.selectedStateFips}
        selectedCountyFips={selectedCountyFips}
        selectedMetroCbsa={selectedMetroCbsa}
        compareIds={homeState.compareIds}
        onBrowseSelectState={(stateFips) => {
          setSelectedCountyFips(null);
          setSelectedMetroCbsa(null);

          applyState((prev) => selectBrowseState(prev, stateFips));
          setIsStateModalOpen(true);
        }}
        onBrowseSelectCounty={(countyFips) => {
          setSelectedMetroCbsa(null);
          setSelectedCountyFips((prev) => (prev === countyFips ? null : countyFips));
        }}
        onBrowseSelectMetro={(cbsaCode) => {
          setSelectedCountyFips(null);
          setSelectedMetroCbsa((prev) => (prev === cbsaCode ? null : cbsaCode));
        }}
        onBrowseLevelChange={(nextLevel) => {
          setSelectedCountyFips(null);
          setSelectedMetroCbsa(null);

          applyState((prev) => setBrowseLevel(prev, nextLevel));
        }}
        onBackToUnitedStates={() => {
          setSelectedCountyFips(null);
          setSelectedMetroCbsa(null);
          setIsStateModalOpen(false);

          applyState((prev) => clearBrowseStateFocus(prev));
        }}
        onBackToState={() => {
          setSelectedCountyFips(null);
          setSelectedMetroCbsa(null);

          applyState((prev) => setBrowseLevel(prev, "county"));
        }}
        onCompareToggle={(id, level) => {
          setSelectedCountyFips(null);
          setSelectedMetroCbsa(null);
          setIsStateModalOpen(false);
          setLevelChangeMessage(null);

          applyState((prev) =>
            toggleCompareSelection(
              {
                ...prev,
                tab: "compare",
              },
              id,
              level
            )
          );
        }}
      />

      {homeState.tab === "compare" ? (
        <section className="home-compare-section">
          {levelChangeMessage ? (
            <div className="compare-level-change-notice" role="status">
              {levelChangeMessage}
            </div>
          ) : null}

          <ComparePanel
            level={compareLevel}
            ids={homeState.compareIds}
            onRemoveId={(id) => {
              applyState((prev) => toggleCompareSelection(prev, id, compareLevel));
              setLevelChangeMessage(null);
            }}
          />
        </section>
      ) : (
        <HomeSummaryPanel
          tab={homeState.tab}
          selectedStateFips={homeState.selectedStateFips}
          selectedCountyFips={selectedCountyFips}
          selectedMetroCbsa={selectedMetroCbsa}
          compareIds={homeState.compareIds}
          summary={summary}
          loading={summaryLoading}
          error={summaryError}
          focusedAreaHref={focusedAreaHref}
          onBackToUnitedStates={() => {
            setSelectedCountyFips(null);
            setSelectedMetroCbsa(null);
            setIsStateModalOpen(false);

            applyState((prev) => clearBrowseStateFocus(prev));
          }}
          onBackToState={() => {
            setSelectedCountyFips(null);
            setSelectedMetroCbsa(null);

            applyState((prev) => setBrowseLevel(prev, "county"));
          }}
          onClearCompareSelections={() => {
            applyState((prev) =>
              normalizeHomeState({ ...prev, compareIds: [] })
            );
          }}
          onAddToCompare={(id, level) => {
          setLevelChangeMessage(null);

          applyState((prev) =>
            toggleCompareSelection(
              {
                ...prev,
                tab: "compare",
              },
              id,
              level
            )
          );
        }}
        />
      )}

      <StateSelectionModal
        isOpen={
          homeState.tab === "browse" &&
          homeState.selectedStateFips !== null &&
          homeState.selectedBrowseLevel === "state" &&
          isStateModalOpen
        }
        stateName={selectedStateName}
        summary={summary}
        onClose={() => setIsStateModalOpen(false)}
        onEnterCountyView={() => {
          setIsStateModalOpen(false);
          applyState((prev) => setBrowseLevel(prev, "county"));
        }}
      />
    </div>
  );
}