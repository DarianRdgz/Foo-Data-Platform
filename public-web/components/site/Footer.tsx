// public-web/components/site/Footer.tsx
import Link from "next/link";

export default function Footer() {
  return (
    <footer className="site-footer">
      <div className="container site-footer-inner">
        <span className="footer-brand">aboutmyarea.net</span>
        <span className="footer-sep">·</span>
        <span className="footer-copy">Foo Data Platform</span>

        <nav className="footer-nav" aria-label="Footer navigation">
          <Link href="/browse" className="footer-link">
            Browse
          </Link>
          <Link href="/compare" className="footer-link">
            Compare
          </Link>
        </nav>
      </div>
    </footer>
  );
}