import { useEffect, useMemo, useState } from 'react'
import { http, getAdminApiKey, setAdminApiKey } from '../../api/http'

function EnabledBadge({ enabled }) {
  return enabled ? (
    <span className="inline-flex items-center rounded-full border border-emerald-200 bg-emerald-50 px-2 py-0.5 text-xs font-semibold text-emerald-900">
      ENABLED
    </span>
  ) : (
    <span className="inline-flex items-center rounded-full border border-slate-200 bg-slate-50 px-2 py-0.5 text-xs font-semibold text-slate-500">
      DISABLED
    </span>
  )
}

function GeoLevelBadge({ level }) {
  const colors = {
    national: 'border-violet-200 bg-violet-50 text-violet-800',
    state:    'border-blue-200 bg-blue-50 text-blue-800',
    county:   'border-amber-200 bg-amber-50 text-amber-800',
    metro:    'border-teal-200 bg-teal-50 text-teal-800',
  }
  const cls = colors[level] || 'border-slate-200 bg-slate-50 text-slate-700'
  return (
    <span className={`inline-flex items-center rounded-full border px-2 py-0.5 text-xs font-semibold ${cls}`}>
      {level}
    </span>
  )
}

function StatCard({ label, value, sub }) {
  return (
    <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
      <div className="text-xs font-semibold tracking-wide text-slate-500">{label}</div>
      <div className="mt-1 text-2xl font-bold text-slate-900">{value}</div>
      {sub && <div className="mt-1 text-xs text-slate-500">{sub}</div>}
    </div>
  )
}

export default function AdminFred() {
  const [apiKey, setApiKey] = useState(getAdminApiKey())
  const canCall = useMemo(() => (apiKey || '').trim().length > 0, [apiKey])

  const [series, setSeries] = useState([])
  const [loading, setLoading] = useState(true)
  const [err, setErr] = useState('')

  const [search, setSearch] = useState('')
  const [filterLevel, setFilterLevel] = useState('')
  const [filterEnabled, setFilterEnabled] = useState('')
  const [filterIngested, setFilterIngested] = useState('')

  async function loadSeries() {
    if (!canCall) {
      setSeries([])
      setLoading(false)
      setErr('Enter an Admin API key to load FRED series.')
      return
    }
    setLoading(true)
    setErr('')
    try {
      setAdminApiKey(apiKey)
      const res = await http.get('/api/admin/fred/series')
      setSeries(res.data || [])
    } catch (e) {
      const code = e?.response?.status
      setErr(code === 401
        ? '401 Unauthorized. Check your Admin API key.'
        : (e?.message || 'Failed to load FRED series.'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadSeries()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [canCall])

  const geoLevels = useMemo(() =>
    Array.from(new Set(series.map(s => s.geoLevel))).sort()
  , [series])

  const filtered = useMemo(() => {
    return series.filter(s => {
      if (search && !s.seriesId.toLowerCase().includes(search.toLowerCase()) &&
          !s.category.toLowerCase().includes(search.toLowerCase()) &&
          !s.geoKey.toLowerCase().includes(search.toLowerCase())) return false
      if (filterLevel && s.geoLevel !== filterLevel) return false
      if (filterEnabled === 'true' && !s.enabled) return false
      if (filterEnabled === 'false' && s.enabled) return false
      if (filterIngested === 'yes' && !s.lastObservationDate) return false
      if (filterIngested === 'no' && s.lastObservationDate) return false
      return true
    })
  }, [series, search, filterLevel, filterEnabled, filterIngested])

  const stats = useMemo(() => ({
    total:    series.length,
    enabled:  series.filter(s => s.enabled).length,
    ingested: series.filter(s => s.lastObservationDate).length,
    neverRun: series.filter(s => !s.lastObservationDate && s.enabled).length,
  }), [series])

  return (
    <div className="space-y-5">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-xl font-semibold text-slate-900">FRED Series Catalog</h1>
          <p className="mt-1 text-sm text-slate-600">
            All series from{' '}
            <span className="font-mono text-xs bg-slate-100 px-1 py-0.5 rounded">fred-series-catalog.yml</span>{' '}
            with last ingested observation date.
          </p>
        </div>

        <div className="w-full sm:w-[420px]">
          <label className="block text-sm font-medium text-slate-700">Admin API Key</label>
          <div className="mt-1 flex gap-2">
            <input
              value={apiKey}
              onChange={(e) => setApiKey(e.target.value)}
              placeholder="Paste FDP_ADMIN_API_KEY value…"
              className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900"
            />
            <button
              onClick={() => loadSeries()}
              className="shrink-0 rounded-lg bg-slate-900 px-3 py-2 text-sm font-semibold text-white hover:bg-slate-800"
            >
              Refresh
            </button>
          </div>
        </div>
      </div>

      {err ? (
        <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-900">
          {err}
        </div>
      ) : null}

      {!loading && series.length > 0 && (
        <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
          <StatCard label="Total Series" value={stats.total} sub="in catalog" />
          <StatCard label="Enabled" value={stats.enabled} sub={`${stats.total - stats.enabled} disabled`} />
          <StatCard label="Have Data" value={stats.ingested} sub="at least one observation" />
          <StatCard label="Never Ingested" value={stats.neverRun} sub="enabled but no data yet" />
        </div>
      )}

      <div className="flex flex-wrap items-end gap-3">
        <div>
          <label className="block text-sm font-medium text-slate-700">Search</label>
          <input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="series ID, category, geo key…"
            className="mt-1 w-64 rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm outline-none focus:border-slate-900"
          />
        </div>

        <div>
          <label className="block text-sm font-medium text-slate-700">Geo Level</label>
          <select
            value={filterLevel}
            onChange={(e) => setFilterLevel(e.target.value)}
            className="mt-1 rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm"
          >
            <option value="">All levels</option>
            {geoLevels.map(l => <option key={l} value={l}>{l}</option>)}
          </select>
        </div>

        <div>
          <label className="block text-sm font-medium text-slate-700">Enabled</label>
          <select
            value={filterEnabled}
            onChange={(e) => setFilterEnabled(e.target.value)}
            className="mt-1 rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm"
          >
            <option value="">All</option>
            <option value="true">Enabled only</option>
            <option value="false">Disabled only</option>
          </select>
        </div>

        <div>
          <label className="block text-sm font-medium text-slate-700">Ingested</label>
          <select
            value={filterIngested}
            onChange={(e) => setFilterIngested(e.target.value)}
            className="mt-1 rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm"
          >
            <option value="">All</option>
            <option value="yes">Has data</option>
            <option value="no">No data yet</option>
          </select>
        </div>

        <div className="ml-auto self-end text-xs text-slate-500">
          Showing <span className="font-semibold text-slate-900">{filtered.length}</span> of {series.length}
        </div>
      </div>

      <div className="overflow-hidden rounded-2xl border border-slate-200 bg-white">
        <div className="overflow-x-auto">
          <table className="min-w-full text-left text-sm">
            <thead className="bg-slate-50 text-xs font-semibold uppercase tracking-wide text-slate-600">
              <tr>
                <th className="px-4 py-3">Series ID</th>
                <th className="px-4 py-3">Category</th>
                <th className="px-4 py-3">Geo Level</th>
                <th className="px-4 py-3">Geo Key</th>
                <th className="px-4 py-3">Enabled</th>
                <th className="px-4 py-3">Last Observation</th>
              </tr>
            </thead>

            <tbody className="divide-y divide-slate-200">
              {loading ? (
                <tr>
                  <td className="px-4 py-4 text-slate-600" colSpan={6}>Loading…</td>
                </tr>
              ) : filtered.length === 0 ? (
                <tr>
                  <td className="px-4 py-4 text-slate-600" colSpan={6}>
                    {series.length === 0
                      ? 'No series found (or API key not set).'
                      : 'No series match the current filters.'}
                  </td>
                </tr>
              ) : (
                filtered.map((s) => (
                  <tr key={s.seriesId} className="hover:bg-slate-50">
                    <td className="px-4 py-3 font-mono text-xs font-semibold text-slate-900">
                      {s.seriesId}
                    </td>
                    <td className="px-4 py-3 font-mono text-xs text-slate-600">
                      {s.category}
                    </td>
                    <td className="px-4 py-3">
                      <GeoLevelBadge level={s.geoLevel} />
                    </td>
                    <td className="px-4 py-3 font-mono text-xs text-slate-700">
                      {s.geoKey}
                    </td>
                    <td className="px-4 py-3">
                      <EnabledBadge enabled={s.enabled} />
                    </td>
                    <td className="px-4 py-3">
                      {s.lastObservationDate
                        ? <span className="font-medium text-emerald-700">{s.lastObservationDate}</span>
                        : <span className="italic text-slate-400">never ingested</span>}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>

      <div className="text-xs text-slate-500">
        Last observation date updates after each successful FRED ingestion run.
        Toggle <span className="font-mono bg-slate-100 px-1 py-0.5 rounded">enabled</span> in{' '}
        <span className="font-mono bg-slate-100 px-1 py-0.5 rounded">fred-series-catalog.yml</span> to add or pause series.
      </div>
    </div>
  )
}