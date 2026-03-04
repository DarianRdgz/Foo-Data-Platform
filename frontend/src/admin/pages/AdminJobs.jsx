import { useEffect, useMemo, useRef, useState } from 'react'
import { http, getAdminApiKey, setAdminApiKey } from '../../api/http'

function fmtInstant(iso) {
  if (!iso) return '—'
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return '—'
  return d.toLocaleString()
}

function StatusBadge({ status }) {
  const base = 'inline-flex items-center rounded-full px-2 py-0.5 text-xs font-semibold border'
  const s = (status || 'NEVER').toUpperCase()

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

export default function AdminJobs() {
  const [apiKey, setApiKey] = useState(getAdminApiKey())
  const [jobs, setJobs] = useState([])
  const [loading, setLoading] = useState(true)
  const [err, setErr] = useState('')
  const [busy, setBusy] = useState({}) // { [jobName]: true }
  const pollRef = useRef(null)

  const canCall = useMemo(() => (apiKey || '').trim().length > 0, [apiKey])

  async function loadJobs({ silent } = { silent: false }) {
    if (!canCall) {
      setJobs([])
      setLoading(false)
      setErr('Enter an Admin API key to load jobs.')
      return
    }

    if (!silent) setLoading(true)
    setErr('')

    try {
      setAdminApiKey(apiKey)
      const res = await http.get('/api/admin/jobs')
      setJobs(res.data || [])
    } catch (e) {
      const status = e?.response?.status
      if (status === 401) {
        setErr('401 Unauthorized. Check your Admin API key (and that FDP_ADMIN_API_KEY is set on backend).')
      } else {
        setErr(e?.message || 'Failed to load jobs.')
      }
    } finally {
      if (!silent) setLoading(false)
    }
  }

  function startPolling() {
    stopPolling()
    pollRef.current = setInterval(() => {
      loadJobs({ silent: true })
    }, 5000)
  }

  function stopPolling() {
    if (pollRef.current) {
      clearInterval(pollRef.current)
      pollRef.current = null
    }
  }

  useEffect(() => {
    // initial load and polling
    loadJobs()
    if (canCall) startPolling()
    return () => stopPolling()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [canCall])

  async function toggleEnabled(job) {
    const name = job.name
    setBusy((b) => ({ ...b, [name]: true }))
    setErr('')

    try {
      setAdminApiKey(apiKey)
      if (job.enabled) {
        await http.post(`/api/admin/jobs/${encodeURIComponent(name)}/disable`)
      } else {
        await http.post(`/api/admin/jobs/${encodeURIComponent(name)}/enable`)
      }
      await loadJobs({ silent: true })
    } catch (e) {
      const status = e?.response?.status
      if (status === 401) setErr('401 Unauthorized while updating. Check API key.')
      else setErr(e?.message || 'Failed to toggle job.')
    } finally {
      setBusy((b) => ({ ...b, [name]: false }))
    }
  }

  async function triggerNow(job) {
    const name = job.name
    setBusy((b) => ({ ...b, [name]: true }))
    setErr('')

    try {
      setAdminApiKey(apiKey)
      await http.post(`/api/admin/jobs/${encodeURIComponent(name)}/trigger`)
      // Optimistic: mark as running immediately so UI feels responsive
      setJobs((prev) =>
        prev.map((j) =>
          j.name === name
            ? {
                ...j,
                lastRunStatus: 'RUNNING',
                lastRunStartedAt: new Date().toISOString(),
              }
            : j,
        ),
      )
      // then let polling catch real status
    } catch (e) {
      const status = e?.response?.status
      if (status === 401) setErr('401 Unauthorized while triggering. Check API key.')
      else setErr(e?.message || 'Failed to trigger job.')
    } finally {
      setBusy((b) => ({ ...b, [name]: false }))
    }
  }

  return (
    <div className="space-y-5">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-xl font-semibold text-slate-900">Jobs</h1>
          <p className="mt-1 text-sm text-slate-600">
            Enable/disable scheduled jobs and trigger runs. Updates every 5 seconds.
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
              onClick={() => loadJobs()}
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

      <div className="overflow-hidden rounded-2xl border border-slate-200 bg-white">
        <div className="overflow-x-auto">
          <table className="min-w-full text-left text-sm">
            <thead className="bg-slate-50 text-xs font-semibold uppercase tracking-wide text-slate-600">
              <tr>
                <th className="px-4 py-3">Name</th>
                <th className="px-4 py-3">Source</th>
                <th className="px-4 py-3">Cron</th>
                <th className="px-4 py-3">Enabled</th>
                <th className="px-4 py-3">Last Status</th>
                <th className="px-4 py-3">Last Started</th>
                <th className="px-4 py-3">Last Finished</th>
                <th className="px-4 py-3 text-right">Actions</th>
              </tr>
            </thead>

            <tbody className="divide-y divide-slate-200">
              {loading ? (
                <tr>
                  <td className="px-4 py-4 text-slate-600" colSpan={8}>
                    Loading…
                  </td>
                </tr>
              ) : jobs.length === 0 ? (
                <tr>
                  <td className="px-4 py-4 text-slate-600" colSpan={8}>
                    No jobs registered (or API key not set).
                  </td>
                </tr>
              ) : (
                jobs.map((j) => {
                  const isBusy = !!busy[j.name]
                  return (
                    <tr key={j.name} className="hover:bg-slate-50">
                      <td className="px-4 py-3 font-medium text-slate-900">{j.name}</td>
                      <td className="px-4 py-3 text-slate-700">{j.source}</td>
                      <td className="px-4 py-3 font-mono text-xs text-slate-700">{j.cron}</td>

                      <td className="px-4 py-3">
                        <button
                          onClick={() => toggleEnabled(j)}
                          disabled={!canCall || isBusy}
                          className={[
                            'inline-flex items-center rounded-full border px-3 py-1 text-xs font-semibold',
                            j.enabled
                              ? 'border-emerald-200 bg-emerald-50 text-emerald-900'
                              : 'border-slate-200 bg-slate-50 text-slate-800',
                            (!canCall || isBusy) ? 'opacity-60' : 'hover:bg-white',
                          ].join(' ')}
                          title="Toggle enabled/disabled"
                        >
                          {j.enabled ? 'ENABLED' : 'DISABLED'}
                        </button>
                      </td>

                      <td className="px-4 py-3">
                        <StatusBadge status={j.lastRunStatus} />
                      </td>

                      <td className="px-4 py-3 text-slate-700">{fmtInstant(j.lastRunStartedAt)}</td>
                      <td className="px-4 py-3 text-slate-700">{fmtInstant(j.lastRunFinishedAt)}</td>

                      <td className="px-4 py-3 text-right">
                        <button
                          onClick={() => triggerNow(j)}
                          disabled={!canCall || isBusy}
                          className="rounded-lg border border-slate-200 bg-white px-3 py-2 text-xs font-semibold text-slate-800 hover:bg-slate-50 disabled:opacity-60"
                        >
                          Trigger now
                        </button>
                      </td>
                    </tr>
                  )
                })
              )}
            </tbody>
          </table>
        </div>
      </div>

      <div className="text-xs text-slate-500">
        Notes: Scheduled runs respect Enabled. Trigger-now runs even if Disabled. Polling refresh is 5 seconds.
      </div>
    </div>
  )
}