"use client";

import { useEffect, useRef } from "react";
import { usePathname } from "next/navigation";

const TELEMETRY_ENDPOINT = "/api/telemetry";

function isEnabled(raw: string | undefined): boolean {
  return raw === "true";
}

function sendPayload(payload: Record<string, unknown>) {
  const body = JSON.stringify(payload);

  if (typeof navigator !== "undefined" && typeof navigator.sendBeacon === "function") {
    const blob = new Blob([body], { type: "application/json" });
    navigator.sendBeacon(TELEMETRY_ENDPOINT, blob);
    return;
  }

  void fetch(TELEMETRY_ENDPOINT, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body,
    keepalive: true,
  }).catch(() => {
    // Telemetry must never surface to users
  });
}

export default function TelemetryBridge() {
  const pathname = usePathname();
  const initialized = useRef(false);
  const analyticsEnabled = isEnabled(process.env.NEXT_PUBLIC_ENABLE_ANALYTICS);
  const errorMonitoringEnabled = isEnabled(process.env.NEXT_PUBLIC_ENABLE_ERROR_MONITORING);

  useEffect(() => {
    if (!analyticsEnabled) return;

    sendPayload({
      type: "pageview",
      path: pathname,
      timestamp: new Date().toISOString(),
    });
  }, [pathname, analyticsEnabled]);

  useEffect(() => {
    if (!errorMonitoringEnabled || initialized.current) return;
    initialized.current = true;

    const handleError = (event: ErrorEvent) => {
      sendPayload({
        type: "client_error",
        path: pathname,
        timestamp: new Date().toISOString(),
        message: event.message ?? "Unknown error",
        source: event.filename ?? null,
      });
    };

    const handleRejection = (event: PromiseRejectionEvent) => {
      sendPayload({
        type: "client_error",
        path: pathname,
        timestamp: new Date().toISOString(),
        message: String(event.reason ?? "Unhandled rejection"),
        source: "promise",
      });
    };

    window.addEventListener("error", handleError);
    window.addEventListener("unhandledrejection", handleRejection);

    return () => {
      window.removeEventListener("error", handleError);
      window.removeEventListener("unhandledrejection", handleRejection);
    };
  }, [errorMonitoringEnabled, pathname]);

  return null;
}