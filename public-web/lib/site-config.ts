export function getSiteUrl(): string {
  const raw = process.env.NEXT_PUBLIC_SITE_URL;

  if (raw) {
    try {
      const parsed = new URL(raw);
      return parsed.toString().replace(/\/$/, "");
    } catch {
      // fall through to local default
    }
  }

  return "http://localhost:3000";
}

export function isAnalyticsEnabled(): boolean {
  return process.env.NEXT_PUBLIC_ENABLE_ANALYTICS === "true";
}

export function isErrorMonitoringEnabled(): boolean {
  return process.env.NEXT_PUBLIC_ENABLE_ERROR_MONITORING === "true";
}