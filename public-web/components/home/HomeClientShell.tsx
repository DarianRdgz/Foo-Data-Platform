// public-web/components/home/HomeClientShell.tsx
"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { getArea, getNationalArea, type AreaResponse } from "@/lib/api";
import {
  buildHomeUrl,
  DEFAULT_HOME_STATE,
  parseHomeUrlState,
  switchHomeTab,
  toggleCompareSelection,
  type HomeBrowseLevel,
  type HomeUrlState,
} from "@/lib/home-query";
import { buildHomeSummary } from "@/lib/home-summary";
import { getStateByCode } from "@/lib/us-states";
import HomeTabs from "@/components/home/HomeTabs";
import HomeSummaryPanel from "@/components/home/HomeSummaryPanel";
import StateMapWorkspace from "@/components/home/StateMapWorkspace";

const BROWSE_LEVELS: HomeBrowseLevel[] = ["state", "county", "metro", "zip"];

export default function HomeClientShell() {
  const router = useRouter();
  const pathname = usePathname() || "/";
  const searchParams = useSearchParams();

  const parsedUrlState = useMemo(() => {
    return parseHomeUrlState(new URLSearchParams(searchParams.toString()));
  }, [searchParams]);

  const [homeState, setHomeState] = useState<HomeUrlState>(parsedUrlState);
  const [summaryCache, setSummaryCache] = useState<Record<string, AreaResponse>>(
    {}
  );
  const [summaryLoading, setSummaryLoading] = useState(true);
  const [summaryError, setSummaryError] = useState<string | null>(null);

  useEffect(() => {
    setHomeState(parsedUrlState);
  }, [parsedUrlState]);

  const applyState = useCallback(
    (updater: (prev: HomeUrlState) => HomeUrlState) => {
      setHomeState((prev) => {
        const next = updater(prev);
        const nextUrl = buildHomeUrl(next, pathname);

        const currentQuery = searchParams.toString();
        const currentUrl = currentQuery ? `${pathname}?${currentQuery}` : pathname;

        if (nextUrl !== currentUrl) {
          router.replace(nextUrl, { scroll: false });
        }

        return next;
      });
    },
    [pathname, router, searchParams]
  );

  const summaryTargetStateFips =
    homeState.tab === "browse"
      ? homeState.selectedStateFips
      : homeState.compareIds[0] ?? null;

  const summaryKey = summaryTargetStateFips ?? "national";

  useEffect(() => {
    let cancelled = false;

    async function loadSummary() {
      if (summaryCache[summaryKey]) {
        setSummaryLoading(false);
        setSummaryError(null);
        return;
      }

      setSummaryLoading(true);
      setSummaryError(null);

      try {
        const area =
          summaryKey === "national"
            ? await getNationalArea()
            : await getArea("state", summaryKey);

        if (!cancelled) {
          setSummaryCache((prev) => ({
            ...prev,
            [summaryKey]: area,
          }));
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
  }, [summaryCache, summaryKey]);

  const summaryArea = summaryCache[summaryKey] ?? null;
  const summary = useMemo(
    () => (summaryArea ? buildHomeSummary(summaryArea) : null),
    [summaryArea]
  );

  const selectedStateName = homeState.selectedStateFips
    ? getStateByCode(homeState.selectedStateFips)?.name ??
      homeState.selectedStateFips
    : null;

  const compareStateNames = homeState.compareIds.map((value) => ({
    fips: value,
    name: getStateByCode(value)?.name ?? value,
  }));

  return (
    <div className="home-shell">
      <section className="home-intro">
        <div>
          <p className="home-kicker">aboutmyarea.net · Homepage shell</p>
          <h1>Explore the country from one shared map workspace</h1>
          <p className="home-lead">
            Browse states on the map or build a compact comparison list without
            leaving the homepage route.
          </p>
        </div>
      </section>

      <HomeTabs
        currentTab={homeState.tab}
        onTabChange={(nextTab) => {
          applyState((prev) => switchHomeTab(prev, nextTab));
        }}
      />

      <section className="home-toolbar">
        <div className="home-toolbar-group">
          <span className="toolbar-label">Browse level</span>

          <div className="level-picker" role="group" aria-label="Browse level">
            {BROWSE_LEVELS.map((level) => (
              <button
                key={level}
                type="button"
                className={`level-pill ${
                  homeState.selectedBrowseLevel === level ? "level-pill-active" : ""
                }`}
                onClick={() => {
                  applyState((prev) => ({
                    ...prev,
                    selectedBrowseLevel: level,
                  }));
                }}
              >
                {level}
              </button>
            ))}
          </div>
        </div>

        <div className="home-toolbar-group">
          {homeState.tab === "browse" ? (
            <>
              <span className="toolbar-label">Selected state</span>
              <div className="selection-strip">
                <span className="selection-chip">
                  {selectedStateName ?? "National default"}
                </span>

                {homeState.selectedStateFips ? (
                  <button
                    type="button"
                    className="text-link-button"
                    onClick={() => {
                      applyState((prev) => ({
                        ...prev,
                        selectedStateFips: null,
                      }));
                    }}
                  >
                    Clear
                  </button>
                ) : null}
              </div>
            </>
          ) : (
            <>
              <span className="toolbar-label">Compare selection</span>
              <div className="selection-strip">
                {compareStateNames.length > 0 ? (
                  compareStateNames.map((entry) => (
                    <span key={entry.fips} className="selection-chip">
                      {entry.name}
                    </span>
                  ))
                ) : (
                  <span className="selection-chip">No states selected yet</span>
                )}

                {compareStateNames.length > 0 ? (
                  <button
                    type="button"
                    className="text-link-button"
                    onClick={() => {
                      applyState((prev) => ({
                        ...prev,
                        compareIds: [],
                      }));
                    }}
                  >
                    Clear all
                  </button>
                ) : null}
              </div>
            </>
          )}
        </div>
      </section>

      <StateMapWorkspace
        tab={homeState.tab}
        selectedBrowseLevel={homeState.selectedBrowseLevel}
        selectedStateFips={homeState.selectedStateFips}
        compareIds={homeState.compareIds}
        onBrowseSelect={(stateFips) => {
          applyState((prev) => ({
            ...prev,
            tab: "browse",
            selectedStateFips: stateFips,
          }));
        }}
        onCompareToggle={(stateFips) => {
          applyState((prev) => ({
            ...toggleCompareSelection(
              {
                ...prev,
                tab: "compare",
              },
              stateFips
            ),
          }));
        }}
      />

      <HomeSummaryPanel
        tab={homeState.tab}
        selectedStateFips={homeState.selectedStateFips}
        compareIds={homeState.compareIds}
        summary={summary}
        loading={summaryLoading}
        error={summaryError}
        onClearBrowseSelection={() => {
          applyState((prev) => ({
            ...prev,
            selectedStateFips: null,
          }));
        }}
        onClearCompareSelections={() => {
          applyState((prev) => ({
            ...prev,
            compareIds: [],
          }));
        }}
      />
    </div>
  );
}