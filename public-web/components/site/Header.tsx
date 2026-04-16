import Link from "next/link";
import HeaderSearch from "@/components/site/HeaderSearch";

export default function Header() {
  return (
    <header className="site-header">
      <div className="container site-header-inner">
        <Link href="/" className="brand" aria-label="aboutmyarea.net home">
          aboutmyarea.net
        </Link>

        <HeaderSearch />

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