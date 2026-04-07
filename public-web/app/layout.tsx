import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "aboutmyarea.net",
  description: "Public web foundation for the aboutmyarea.net consumer product.",
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
          <header className="site-header">
            <div className="container">
              <div className="brand">aboutmyarea.net</div>
              <div className="tagline">Public web foundation</div>
            </div>
          </header>

          <main className="site-main">
            <div className="container">{children}</div>
          </main>

          <footer className="site-footer">
            <div className="container">
              <span>Foo Data Platform</span>
              <span> • </span>
              <span>public-web scaffold</span>
            </div>
          </footer>
        </div>
      </body>
    </html>
  );
}
