// public-web/app/layout.tsx
import type { Metadata } from "next";
import "./globals.css";
import Header from "@/components/site/Header";
import Footer from "@/components/site/Footer";
import TelemetryBridge from "../components/site/TelemetryBridge";
import { getSiteUrl } from "@/lib/site-config";

export const metadata: Metadata = {
  metadataBase: new URL(getSiteUrl()),
  title: {
    template: "%s · aboutmyarea.net",
    default: "aboutmyarea.net",
  },
  description:
    "Browse and compare neighborhoods, counties, and metro areas across the United States. Explore housing, economic, and risk data on aboutmyarea.net.",
  openGraph: {
    siteName: "aboutmyarea.net",
    type: "website",
  },
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>
        <TelemetryBridge />
        <div className="site-shell">
          <Header />
          <main className="site-main">
            <div className="container">{children}</div>
          </main>
          <Footer />
        </div>
      </body>
    </html>
  );
}