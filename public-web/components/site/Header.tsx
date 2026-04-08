// public-web/components/site/Header.tsx
import Link from "next/link";

export default function Header() {
  return (
    <header className="site-header">
      <div className="container site-header-inner">
        <Link href="/" className="brand" aria-label="aboutmyarea.net home">
          aboutmyarea.net
        </Link>

        <nav className="site-nav" aria-label="Main navigation">
          <Link href="/browse" className="nav-link">
            Browse
          </Link>
          <Link href="/compare" className="nav-link">
            Compare
          </Link>
        </nav>
      </div>
    </header>
  );
}