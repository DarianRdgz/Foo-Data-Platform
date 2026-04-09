// public-web/components/home/HomeTabs.tsx
"use client";

import type { HomeTab } from "@/lib/home-query";

interface Props {
  currentTab: HomeTab;
  onTabChange: (tab: HomeTab) => void;
}

export default function HomeTabs({ currentTab, onTabChange }: Props) {
  return (
    <div className="home-tabs" role="tablist" aria-label="Homepage mode">
      <button
        type="button"
        role="tab"
        aria-selected={currentTab === "browse"}
        className={`home-tab ${currentTab === "browse" ? "home-tab-active" : ""}`}
        onClick={() => onTabChange("browse")}
      >
        Browse
      </button>

      <button
        type="button"
        role="tab"
        aria-selected={currentTab === "compare"}
        className={`home-tab ${currentTab === "compare" ? "home-tab-active" : ""}`}
        onClick={() => onTabChange("compare")}
      >
        Compare
      </button>
    </div>
  );
}