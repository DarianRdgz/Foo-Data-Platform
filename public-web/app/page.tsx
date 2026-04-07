import { getMapTiles } from "@/lib/api";

export default async function HomePage() {
  let stateCount: number | null = null;
  let apiStatus = "Connected";

  try {
    const tiles = await getMapTiles("state", "-180,-90,180,90");
    stateCount = tiles.features.length;
  } catch (error) {
    apiStatus = "Unavailable";
    console.error(error);
  }

  return (
    <section className="hero-card">
      <p className="eyebrow">Sprint 6.4</p>
      <h1>Public web scaffold is running.</h1>
      <p className="lead">
        This page is intentionally minimal. Its job is to prove that the new
        Next.js app can run independently and talk to the existing backend
        through NEXT_PUBLIC_API_BASE_URL.
      </p>

      <div className="status-grid">
        <div className="status-item">
          <span className="label">App</span>
          <strong>public-web</strong>
        </div>
        <div className="status-item">
          <span className="label">API status</span>
          <strong>{apiStatus}</strong>
        </div>
        <div className="status-item">
          <span className="label">State tile count</span>
          <strong>{stateCount ?? "n/a"}</strong>
        </div>
      </div>
    </section>
  );
}
