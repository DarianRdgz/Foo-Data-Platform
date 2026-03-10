import { useEffect, useMemo, useRef, useState } from 'react'
import { http, getAdminApiKey, setAdminApiKey } from '../../api/http'

function fmt(iso) {
  if (!iso) return '—'
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return '—'
  return d.toLocaleString()
}

function msToHuman(ms) {
  if (ms == null) return '—'
  const s = Math.max(0, Math.floor(ms / 1000))
  const m = Math.floor(s / 60)
  const r = s % 60
  if (m <= 0) return `${r}s`
  return `${m}m ${r}s`
}

function StatusBadge({ status }) {
  const s = (status || 'UNKNOWN').toUpperCase()
  const base = 'inline-flex items-center rounded-full px-2 py-0.5 text-xs font-semibold border'
  const cls =
    s === 'SUCCESS'
      ? 'border-emerald-200 bg-emerald-50 text-emerald-900'
      : s === 'FAILED'
        ? 'border-red-200 bg-red-50 text-red-900'
        : s === 'RUNNING'
          ? 'border-amber-200 bg-amber-50 text-amber-900'
          : 'border-slate-200 bg-slate-50 text-slate-800'
  return <span className={`${base} ${cls}`}>{s}</span>
}

export default function AdminIngestion() {
  const [apiKey, setApiKey] = useState(getAdminApiKey())
  const canCall = useMemo(() => (apiKey || '').trim().length > 0, [apiKey])

  // filters + pagination
  const [source, setSource] = useState('')
  const [status, setStatus] = useState('')
  const [page, setPage] = useState(0)
  const size = 25

  // data
  const [quota, setQuota] = useState([])
  const [runsPage, setRunsPage] = useState({ items: [], page: 0, size, total: 0 })
  const [loading, setLoading] = useState(true)
  const [err, setErr] = useState('')

  // drawer
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [selectedId, setSelectedId] = useState(null)
  const [detail, setDetail] = useState(null)
  const [detailLoading, setDetailLoading] = useState(false)

  // refs to avoid stale closure inside polling interval (Issue 2)
  const drawerOpenRef = useRef(false)
  const selectedIdRef = useRef(null)

  useEffect(() => { drawerOpenRef.current = drawerOpen }, [drawerOpen])
  useEffect(() => { selectedIdRef.current = selectedId }, [selectedId])

  // live duration tick
  const [nowTick, setNowTick] = useState(Date.now())

  const sourcesForFilter = useMemo(() => {
    const set = new Set()
    for (const q of quota || []) if (q?.source) set.add(q.source)
    for (const r of runsPage.items || []) if (r?.source) set.add(r.source)
    return Array.from(set).sort()
  }, [quota, runsPage.items])

  const totalPages = useMemo(() => {
    const t = runsPage?.total || 0
    return Math.max(1, Math.ceil(t / size))
  }, [runsPage?.total])

  async function loadQuota({ silent = false } = {}) {
    if (!canCall) return
    try {
      setAdminApiKey(apiKey)
      const res = await http.get('/api/admin/ingestion/quota')
      setQuota(res.data || [])
    } catch (e) {
      if (!silent) throw e
    }
  }

  async function loadRuns({ silent = false, pageOverride } = {}) {
    if (!canCall) {
      setRunsPage({ items: [], page: 0, size, total: 0 })
      setLoading(false)
      setErr('Enter an Admin API key to load ingestion runs.')
      return
    }

    if (!silent) setLoading(true)
    setErr('')

    try {
      setAdminApiKey(apiKey)

      const p = typeof pageOverride === 'number' ? pageOverride : page

      const params = new URLSearchParams()
      params.set('page', String(p))
      params.set('size', String(size))
      if (source) params.set('source', source)
      if (status) params.set('status', status)

      const res = await http.get(`/api/admin/ingestion/runs?${params.toString()}`)
      setRunsPage(res.data)
    } catch (e) {
      const code = e?.response?.status
      if (code === 401) {
        setErr('401 Unauthorized. Check Admin API key (and backend FDP_ADMIN_API_KEY).')
      } else {
        setErr(e?.message || 'Failed to load runs.')
      }
    } finally {
      if (!silent) setLoading(false)
    }
  }

  async function fetchDetail(runId, { silent = false } = {}) {
    if (!canCall || !runId) return
    if (!silent) setDetailLoading(true)
    try {
      setAdminApiKey(apiKey)
      const res = await http.get(`/api/admin/ingestion/runs/${runId}`)
      setDetail(res.data)
    } catch (e) {
      if (!silent) setErr(e?.message || 'Failed to load run detail.')
    } finally {
      if (!silent) setDetailLoading(false)
    }
  }

  async function loadAll({ silent = false } = {}) {
    if (!silent) setLoading(true)
    try {
      await Promise.all([loadQuota({ silent: true }), loadRuns({ silent: true })])
    } finally {
      if (!silent) setLoading(false)
    }
  }

  // Single effect handles filters+page without double loading (Issue 4)
  // If filter changes while on page>0, we reset page and fetch once with pageOverride=0.
  const prevFiltersRef = useRef({ source: '', status: '' })
  useEffect(() => {
    const prev = prevFiltersRef.current
    const filtersChanged = prev.source !== source || prev.status !== status
    prevFiltersRef.current = { source, status }

    if (filtersChanged && page !== 0) {
      setPage(0)
      loadRuns({ pageOverride: 0 })
      return
    }

    loadRuns()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [source, status, page])

  // Initial load + start polling
  useEffect(() => {
    loadAll()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [canCall])

  // Polling interval (refresh runs+quota; refresh drawer detail safely via refs)
  useEffect(() => {
    if (!canCall) return

    const id = setInterval(() => {
      setNowTick(Date.now())
      loadRuns({ silent: true })
      loadQuota({ silent: true })

      const open = drawerOpenRef.current
      const runId = selectedIdRef.current
      if (open && runId) {
        fetchDetail(runId, { silent: true })
      }
    }, 5000)

    return () => clearInterval(id)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [canCall, apiKey, source, status, page])

  async function openDrawer(run) {
    setSelectedId(run.id)
    setDrawerOpen(true)
    setDetail(null)
    await fetchDetail(run.id)
  }

  function closeDrawer() {
    setDrawerOpen(false)
    setSelectedId(null)
    setDetail(null)
  }

  return (
    <div className="space-y-5">
      {/* Header + API key */}
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-xl font-semibold text-slate-900">Ingestion</h1>
          <p className="mt-1 text-sm text-slate-600">
            Run history, live RUNNING durations, and API quota usage (refreshes every 5 seconds).
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
              onClick={() => loadAll()}
              className="shrink-0 rounded-lg bg-slate-900 px-3 py-2 text-sm font-semibold text-white hover:bg-slate-800"
            >
              Load
            </button>
          </div>
        </div>
      </div>

      {err ? (
        <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-900">
          {err}
        </div>
      ) : null}

      {/* Quota cards */}
      <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
        {(quota || []).map((q) => (
          <div key={q.source} className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
            <div className="text-xs font-semibold tracking-wide text-slate-500">{q.source}</div>
            <div className="mt-2 text-sm text-slate-700 space-y-1">
              <div className="flex items-center justify-between">
                <span>Used today</span>
                <span className="font-semibold text-slate-900">{q.usedToday}</span>
              </div>
              <div className="flex items-center justify-between">
                <span>Daily limit</span>
                <span className="font-semibold text-slate-900">{q.dailyLimit}</span>
              </div>
              <div className="flex items-center justify-between">
                <span>Remaining</span>
                <span className="font-semibold text-slate-900">{q.remaining}</span>
              </div>
            </div>
            <div className="mt-3 text-xs text-slate-500">Date: {q.usageDate}</div>
          </div>
        ))}
      </div>

      {/* Filters */}
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div className="flex gap-3">
          <div>
            <label className="block text-sm font-medium text-slate-700">Source</label>
            <select
              value={source}
              onChange={(e) => setSource(e.target.value)}
              className="mt-1 rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm"
            >
              <option value="">All</option>
              {sourcesForFilter.map((s) => (
                <option key={s} value={s}>{s}</option>
              ))}
            </select>
          </div>

          <div>
            <label className="block text-sm font-medium text-slate-700">Status</label>
            <select
              value={status}
              onChange={(e) => setStatus(e.target.value)}
              className="mt-1 rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm"
            >
              <option value="">All</option>
              <option value="RUNNING">RUNNING</option>
              <option value="SUCCESS">SUCCESS</option>
              <option value="FAILED">FAILED</option>
            </select>
          </div>
        </div>

        <div className="text-xs text-slate-500">
          Total: <span className="font-semibold text-slate-900">{runsPage.total || 0}</span>
        </div>
      </div>

      {/* Runs table */}
      <div className="overflow-hidden rounded-2xl border border-slate-200 bg-white">
        <div className="overflow-x-auto">
          <table className="min-w-full text-left text-sm">
            <thead className="bg-slate-50 text-xs font-semibold uppercase tracking-wide text-slate-600">
              <tr>
                <th className="px-4 py-3">Source</th>
                <th className="px-4 py-3">Status</th>
                <th className="px-4 py-3">Started</th>
                <th className="px-4 py-3">Finished</th>
                <th className="px-4 py-3">Duration</th>
                <th className="px-4 py-3">Records</th>
              </tr>
            </thead>

            <tbody className="divide-y divide-slate-200">
              {loading ? (
                <tr>
                  <td className="px-4 py-4 text-slate-600" colSpan={6}>Loading…</td>
                </tr>
              ) : (runsPage.items || []).length === 0 ? (
                <tr>
                  <td className="px-4 py-4 text-slate-600" colSpan={6}>No runs found.</td>
                </tr>
              ) : (
                runsPage.items.map((r) => {
                  const isRunning = (r.status || '').toUpperCase() === 'RUNNING'
                  const startedMs = r.startedAt ? new Date(r.startedAt).getTime() : null
                  const liveDurationMs = isRunning && startedMs ? (nowTick - startedMs) : r.durationMs

                  return (
                    <tr
                      key={r.id}
                      className="cursor-pointer hover:bg-slate-50"
                      onClick={() => openDrawer(r)}
                      title="Click for details"
                    >
                      <td className="px-4 py-3 font-medium text-slate-900">{r.source}</td>
                      <td className="px-4 py-3"><StatusBadge status={r.status} /></td>
                      <td className="px-4 py-3 text-slate-700">{fmt(r.startedAt)}</td>
                      <td className="px-4 py-3 text-slate-700">{fmt(r.finishedAt)}</td>
                      <td className="px-4 py-3 text-slate-700">{msToHuman(liveDurationMs)}</td>
                      <td className="px-4 py-3 text-slate-700">{r.recordsWritten}</td>
                    </tr>
                  )
                })
              )}
            </tbody>
          </table>
        </div>

        {/* Pagination */}
        <div className="flex items-center justify-between border-t border-slate-200 px-4 py-3 text-sm">
          <div className="text-slate-600">
            Page <span className="font-semibold text-slate-900">{page + 1}</span> / {totalPages}
          </div>

          <div className="flex items-center gap-2">
            <button
              className="rounded-lg border border-slate-200 bg-white px-3 py-2 text-xs font-semibold text-slate-800 hover:bg-slate-50 disabled:opacity-60"
              disabled={page <= 0}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
            >
              Prev
            </button>
            <button
              className="rounded-lg border border-slate-200 bg-white px-3 py-2 text-xs font-semibold text-slate-800 hover:bg-slate-50 disabled:opacity-60"
              disabled={page + 1 >= totalPages}
              onClick={() => setPage((p) => p + 1)}
            >
              Next
            </button>
          </div>
        </div>
      </div>

      {/* Drawer */}
      {drawerOpen ? (
        <div className="fixed inset-0 z-50">
          <div className="absolute inset-0 bg-black/30" onClick={closeDrawer} />
          <div className="absolute right-0 top-0 h-full w-full max-w-2xl bg-white shadow-xl">
            <div className="flex items-center justify-between border-b border-slate-200 px-5 py-4">
              <div>
                <div className="text-xs font-semibold tracking-wide text-slate-500">Run detail</div>
                <div className="mt-1 text-sm text-slate-700 font-mono">{selectedId}</div>
              </div>
              <button
                onClick={closeDrawer}
                className="rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm font-semibold text-slate-800 hover:bg-slate-50"
              >
                Close
              </button>
            </div>

            <div className="p-5">
              {detailLoading || !detail ? (
                <div className="text-sm text-slate-600">Loading detail…</div>
              ) : (
                <div className="space-y-4">
                  <div className="flex items-center gap-2">
                    <StatusBadge status={detail.status} />
                    <div className="text-sm text-slate-700">{detail.source}</div>
                  </div>

                  <div className="grid grid-cols-2 gap-3 text-sm">
                    <div className="rounded-xl border border-slate-200 bg-slate-50 p-3">
                      <div className="text-xs text-slate-500">Started</div>
                      <div className="mt-1 text-slate-900">{fmt(detail.startedAt)}</div>
                    </div>
                    <div className="rounded-xl border border-slate-200 bg-slate-50 p-3">
                      <div className="text-xs text-slate-500">Finished</div>
                      <div className="mt-1 text-slate-900">{fmt(detail.finishedAt)}</div>
                    </div>
                    <div className="rounded-xl border border-slate-200 bg-slate-50 p-3">
                      <div className="text-xs text-slate-500">Duration</div>
                      <div className="mt-1 text-slate-900">{msToHuman(detail.durationMs)}</div>
                    </div>
                    <div className="rounded-xl border border-slate-200 bg-slate-50 p-3">
                      <div className="text-xs text-slate-500">Records written</div>
                      <div className="mt-1 text-slate-900">{detail.recordsWritten}</div>
                    </div>
                  </div>

                  <div className="rounded-xl border border-slate-200 p-4">
                    <div className="text-xs font-semibold tracking-wide text-slate-500">Message</div>
                    <div className="mt-2 text-sm text-slate-900 whitespace-pre-wrap">
                      {detail.message || '—'}
                    </div>
                  </div>

                  <div className="rounded-xl border border-slate-200 p-4">
                    <div className="text-xs font-semibold tracking-wide text-slate-500">Requested scope</div>
                    <pre className="mt-2 overflow-auto max-h-40 rounded-lg bg-slate-50 p-3 text-xs text-slate-800">
{detail.requestedScopeJson || '—'}
                    </pre>
                  </div>

                  {(detail.status || '').toUpperCase() === 'FAILED' ? (
                    <div className="rounded-xl border border-red-200 bg-red-50 p-4">
                      <div className="text-xs font-semibold tracking-wide text-red-800">Error detail</div>
                      <pre className="mt-2 overflow-auto max-h-64 rounded-lg bg-white/60 p-3 text-xs text-red-900">
{detail.errorDetail || '—'}
                      </pre>
                    </div>
                  ) : null}
                </div>
              )}
            </div>
          </div>
        </div>
      ) : null}
    </div>
  )
}