// public-web/app/page.tsx
import type { Metadata } from "next";
import Link from "next/link";

export const metadata: Metadata = {
  title: "aboutmyarea.net",
  description: "Public web foundation for the aboutmyarea.net consumer product.",
};

const EXAMPLE_ROUTES = [
  {
    label: "State",
    title: "Texas state detail",
    path: "/area/state/48",
    desc: "Canonical area detail route for a state.",
  },
  {
    label: "County",
    title: "Harris County, TX",
    path: "/area/county/48201",
    desc: "Canonical area detail route for a county.",
  },
  {
    label: "Metro",
    title: "Houston metro",
    path: "/area/metro/26420",
    desc: "Canonical area detail route for a CBSA / metro.",
  },
  {
    label: "ZIP",
    title: "ZIP 77002",
    path: "/area/zip/77002",
    desc: "Canonical area detail route for a ZIP code.",
  },
  {
    label: "Browse",
    title: "Browse Texas",
    path: "/browse/48",
    desc: "Crawlable state browse entry route.",
  },
  {
    label: "Compare",
    title: "County comparison",
    path: "/compare?level=county&ids=48201,06037",
    desc: "Public compare route scaffold.",
  },
] as const;

export default function HomePage() {
  return (
    <div className="hero-card">
      <p className="eyebrow">aboutmyarea.net · Public Beta Foundation</p>
      <h1>Explore your area</h1>
      <p className="lead">
        This public-web scaffold establishes the shared shell, route structure,
        and typed API client for the aboutmyarea.net public product.
      </p>

      <div className="hero-actions">
        <Link href="/browse" className="stub-link">
          Browse all states
        </Link>
        <Link href="/compare" className="stub-link">
          Compare areas
        </Link>
      </div>

      <p className="section-eyebrow">Representative public routes</p>
      <div className="route-grid">
        {EXAMPLE_ROUTES.map((route) => (
          <Link key={route.path} href={route.path} className="route-card">
            <span className="route-card-label">{route.label}</span>
            <span className="route-card-title">{route.title}</span>
            <span className="route-card-path">{route.path}</span>
            <span className="route-card-desc">{route.desc}</span>
          </Link>
        ))}
      </div>
    </div>
  );
}