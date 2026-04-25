// public-web/app/page.tsx
import type { Metadata } from "next";
import { buildPageMetadata } from "@/lib/metadata";
import { Suspense } from "react";
import HomeClientShell from "@/components/home/HomeClientShell";

export const metadata: Metadata = buildPageMetadata({
  title: "aboutmyarea.net",
  description:
    "Browse and compare neighborhoods, counties, and metro areas across the United States. Explore housing, economic, and risk data for the places that matter to you.",
  path: "/",
});

export default function HomePage() {
  return (
    <Suspense fallback={<div className="map-placeholder">Loading homepage…</div>}>
      <HomeClientShell />
    </Suspense>
  );
}