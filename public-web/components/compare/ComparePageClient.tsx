"use client";

import { useCallback, useMemo } from "react";
import { useRouter, useSearchParams } from "next/navigation";

import { ComparePanel } from "@/components/compare/ComparePanel";
import {
  buildDirectCompareUrl,
  parseDirectCompareParams,
} from "@/lib/compare-query";
import type { ComparableGeoLevel } from "@/lib/compare-validation";

interface ComparePageClientProps {
  initialLevel: ComparableGeoLevel | null;
  initialIds: string[];
}

export function ComparePageClient({
  initialLevel,
  initialIds,
}: ComparePageClientProps) {
  const router = useRouter();
  const searchParams = useSearchParams();

  const currentCompareState = useMemo(() => {
    const level = searchParams.get("level");
    const ids = searchParams.get("ids");

    if (level === null && ids === null) {
      return {
        level: initialLevel,
        ids: initialIds,
      };
    }

    return parseDirectCompareParams({ level, ids });
  }, [searchParams, initialLevel, initialIds]);

  const handleRemoveId = useCallback(
    (idToRemove: string) => {
      const nextIds = currentCompareState.ids.filter((id) => id !== idToRemove);
      const nextLevel = nextIds.length === 0 ? null : currentCompareState.level;

      const nextUrl = buildDirectCompareUrl({
        level: nextLevel,
        ids: nextIds,
      });

      router.replace(nextUrl, { scroll: false });
    },
    [currentCompareState, router]
  );

  return (
    <ComparePanel
      level={currentCompareState.level}
      ids={currentCompareState.ids}
      onRemoveId={handleRemoveId}
      standalone
    />
  );
}