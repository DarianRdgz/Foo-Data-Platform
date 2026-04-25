import type { MetadataRoute } from "next";
import { getSiteUrl } from "@/lib/site-config";
import { US_STATES } from "@/lib/us-states";

export default function sitemap(): MetadataRoute.Sitemap {
  const base = getSiteUrl();

  const staticRoutes: MetadataRoute.Sitemap = [
    { url: `${base}/`, changeFrequency: "weekly", priority: 1.0 },
    { url: `${base}/browse`, changeFrequency: "monthly", priority: 0.7 },
    { url: `${base}/compare`, changeFrequency: "monthly", priority: 0.6 },
  ];

  const stateRoutes: MetadataRoute.Sitemap = US_STATES.flatMap(({ code }) => [
    {
      url: `${base}/browse/${code}`,
      changeFrequency: "monthly" as const,
      priority: 0.6,
    },
    {
      url: `${base}/area/state/${code}`,
      changeFrequency: "weekly" as const,
      priority: 0.8,
    },
  ]);

  return [...staticRoutes, ...stateRoutes];
}