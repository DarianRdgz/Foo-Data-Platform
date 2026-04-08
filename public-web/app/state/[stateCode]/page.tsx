// public-web/app/state/[stateCode]/page.tsx
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
    return { title: "State" };
  }

  const state = getStateByCode(stateCode);
  return {
    title: state ? `${state.name}` : `State ${stateCode}`,
  };
}

export default async function StatePage({ params }: Props) {
  const { stateCode } = await params;

  if (!isValidStateCode(stateCode)) {
    notFound();
  }

  const state = getStateByCode(stateCode);
  const stateName = state?.name ?? `State ${stateCode}`;

  return (
    <div className="stub-page">
      <p className="stub-page-eyebrow">State drilldown</p>
      <h1>{stateName}</h1>
      <p className="stub-desc">
        This is the state overview route. Full county drilldown content will be
        wired in Sprint 8.
      </p>

      <div className="stub-meta">
        <span className="stub-badge">stateCode: {stateCode}</span>
        <span className="stub-badge">route: /state/[stateCode]</span>
      </div>

      <div className="stub-links">
        <Link href={`/browse/${stateCode}`} className="stub-link">
          Browse this state
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