// public-web/app/layout.tsx
import type { Metadata } from "next";
import "./globals.css";
import Header from "@/components/site/Header";
import Footer from "@/components/site/Footer";

export const metadata: Metadata = {
  title: {
    template: "%s · aboutmyarea.net",
    default: "aboutmyarea.net",
  },
  description:
    "Public web foundation for the aboutmyarea.net consumer product.",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en">
      <body>
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