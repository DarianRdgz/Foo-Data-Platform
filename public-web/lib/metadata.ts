import type { Metadata } from "next";
import { getSiteUrl } from "@/lib/site-config";

export interface PageMetadataInput {
  title: string;
  description: string;
  path: string;
}

export function buildPageMetadata(input: PageMetadataInput): Metadata {
  const siteUrl = getSiteUrl();
  const url = `${siteUrl}${input.path}`;

  return {
    title: input.title,
    description: input.description,
    alternates: { canonical: url },
    openGraph: {
      title: input.title,
      description: input.description,
      url,
      siteName: "aboutmyarea.net",
      type: "website",
    },
    twitter: {
      card: "summary_large_image",
      title: input.title,
      description: input.description,
    },
  };
}