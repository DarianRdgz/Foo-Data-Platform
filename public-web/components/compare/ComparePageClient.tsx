// public-web/components/compare/ComparePageClient.tsx
"use client";

import { ComparePanel } from "@/components/compare/ComparePanel";

interface ComparePageClientProps {
  level: string | null;
  ids: string[];
}

export function ComparePageClient({ level, ids }: ComparePageClientProps) {
  return (
    <ComparePanel
      level={level}
      ids={ids}
      standalone
    />
  );
}