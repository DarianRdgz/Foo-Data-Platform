// public-web/app/browse/[stateCode]/page.tsx
import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { isValidStateCode } from "@/lib/route-contract";
import { getStateByCode } from "@/lib/us-states";

interface Props {
  params: Promise<{ stateCode: string }>;
}

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { stateCode } = await params;

  if (!isValidStateCode(stateCode)) {
    return { title: "Browse state" };
  }

  const state = getStateByCode(stateCode);
  return {
    title: state ? `Browse ${state.name}` : `Browse ${stateCode}`,
  };
}

export default async function BrowseStatePage({ params }: Props) {
  const { stateCode } = await params;

  if (!isValidStateCode(stateCode)) {
    notFound();
  }

  const state = getStateByCode(stateCode);
  const stateName = state?.name ?? `State ${stateCode}`;

  return (
    <div className="stub-page">
      <p className="stub-page-eyebrow">Browse · State</p>
      <h1>{stateName}</h1>
      <p className="stub-desc">
        This crawlable state browse page will become the entry point for county,
        metro, and ZIP exploration in Sprint 8.
      </p>

      <div className="stub-meta">
        <span className="stub-badge">stateCode: {stateCode}</span>
        <span className="stub-badge">route: /browse/[stateCode]</span>
      </div>

      <div className="stub-links">
        <Link href={`/state/${stateCode}`} className="stub-link">
          State overview
        </Link>
        <Link href={`/area/state/${stateCode}`} className="stub-link">
          Canonical area detail
        </Link>
        <Link href="/browse" className="stub-link">
          ← All states
        </Link>
      </div>
    </div>
  );
}