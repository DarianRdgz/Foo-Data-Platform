// public-web/app/state/[stateCode]/page.tsx
import { notFound, redirect } from "next/navigation";
import { isValidStateCode } from "@/lib/route-contract";
import { buildHomeUrl } from "@/lib/home-query";

interface Props {
  params: Promise<{ stateCode: string }>;
}

export default async function StatePage({ params }: Props) {
  const { stateCode } = await params;

  if (!isValidStateCode(stateCode)) {
    notFound();
  }

  redirect(
    buildHomeUrl({
      tab: "browse",
      selectedStateFips: stateCode,
      selectedBrowseLevel: "state",
      compareIds: [],
    })
  );
}