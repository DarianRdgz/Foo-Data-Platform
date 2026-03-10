import { useEffect, useState } from 'react'

function fmt(iso) {
  if (!iso) return '—'
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return '—'
  return d.toLocaleString()
}

function StatusBadge({ status }) {
  const s = (status || 'NEVER').toUpperCase()
  const base = 'inline-flex items-center rounded-full px-2 py-0.5 text-xs font-semibold border'
  const cls =
    s === 'SUCCESS'
      ? 'border-emerald-200 bg-emerald-50 text-emerald-900'
      : s === 'FAILED'
        ? 'border-red-200 bg-red-50 text-red-900'
        : s === 'RUNNING'
          ? 'border-amber-200 bg-amber-50 text-amber-900'
          : 'border-slate-200 bg-slate-50 text-slate-700'
  return <span className={`${base} ${cls}`}>{s}</span>
}

export default function AdminOverview() {
  const [health, setHealth] = useState(null)
  const [err, setErr] = useState('')

  useEffect(() => {
    fetch('/api/health')
      .then((r) => {
        if (!r.ok) throw new Error(`HTTP ${r.status}`)
        return r.json()
      })
      .then(setHealth)
      .catch((e) => setErr(e.message || 'Failed to load health'))
  }, [])

  return (
    <div className="space-y-5">
      <div>
        <h1 className="text-xl font-semibold text-slate-900">Overview</h1>
        <p className="mt-1 text-sm text-slate-600">
          Last ingestion run status per source. Use the Jobs tab to trigger runs or change schedules.
        </p>
      </div>

      {err ? (
        <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-900">
          {err}
        </div>
      ) : !health ? (
        <div className="text-sm text-slate-600">Loading…</div>
      ) : (
        <>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {(health.sources || []).length === 0 ? (
              <div className="col-span-full rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-600">
                No ingestion runs recorded yet.
              </div>
            ) : (
              (health.sources || []).map((s) => (
                <div key={s.source} className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
                  <div className="flex items-center justify-between">
                    <div className="text-xs font-semibold tracking-wide text-slate-500 uppercase">
                      {s.source}
                    </div>
                    <StatusBadge status={s.status} />
                  </div>
                  <div className="mt-3 space-y-1.5 text-xs text-slate-600">
                    <div className="flex justify-between">
                      <span>Started</span>
                      <span className="text-slate-900">{fmt(s.startedAt)}</span>
                    </div>
                    <div className="flex justify-between">
                      <span>Finished</span>
                      <span className="text-slate-900">{fmt(s.finishedAt)}</span>
                    </div>
                    {s.message && (
                      <div className="mt-2 rounded-lg border border-slate-100 bg-slate-50 px-2 py-1.5 font-mono text-[11px] text-slate-700 break-words">
                        {s.message}
                      </div>
                    )}
                  </div>
                </div>
              ))
            )}
          </div>
          <div className="text-xs text-slate-400">
            Generated at {fmt(health.generatedAt)}
          </div>
        </>
      )}
    </div>
  )
}