// public-web/app/page.tsx
import type { Metadata } from "next";
import { Suspense } from "react";
import HomeClientShell from "@/components/home/HomeClientShell";

export const metadata: Metadata = {
  title: "aboutmyarea.net",
  description:
    "Browse and compare places across the United States from the aboutmyarea.net homepage map shell.",
};

export default function HomePage() {
  return (
    <Suspense fallback={<div className="map-placeholder">Loading homepage…</div>}>
      <HomeClientShell />
    </Suspense>
  );
}