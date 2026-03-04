import { useEffect, useMemo, useState } from 'react'
import { http, getAdminApiKey, setAdminApiKey } from '../../api/http'

function prettyTime(iso) {
  if (!iso) return '—'
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return '—'
  return d.toLocaleString()
}

function bytesBadge(bytes, pretty) {
  if (pretty) return pretty
  if (bytes == null) return '—'
  const b = Number(bytes)
  if (!Number.isFinite(b)) return '—'
  if (b < 1024) return `${b} B`
  const kb = b / 1024
  if (kb < 1024) return `${kb.toFixed(1)} KB`
  const mb = kb / 1024
  if (mb < 1024) return `${mb.toFixed(1)} MB`
  const gb = mb / 1024
  return `${gb.toFixed(2)} GB`
}

function tryFormatJson(val) {
  if (val == null) return null
  if (typeof val === 'object') {
    try { return JSON.stringify(val, null, 2) } catch { return String(val) }
  }
  if (typeof val === 'string') {
    const s = val.trim()
    if ((s.startsWith('{') && s.endsWith('}')) || (s.startsWith('[') && s.endsWith(']'))) {
      try { return JSON.stringify(JSON.parse(s), null, 2) } catch { return null }
    }
  }
  return null
}

export default function AdminDatabase() {
  const [apiKey, setApiKey] = useState(getAdminApiKey())
  const canCall = useMemo(() => (apiKey || '').trim().length > 0, [apiKey])

  const [tables, setTables] = useState([])
  const [loading, setLoading] = useState(true)
  const [err, setErr] = useState('')

  const [openKey, setOpenKey] = useState(null) // "schema.table"
  const [columns, setColumns] = useState([])
  const [indexes, setIndexes] = useState([])
  const [sample, setSample] = useState(null)
  const [detailLoading, setDetailLoading] = useState(false)

  const sampleHeaders = useMemo(() => {
    const row0 = sample?.rows?.[0]
    if (row0) return Object.keys(row0)
    // Fallback to schema columns if table is empty (fixes the “empty thead” issue)
    return (columns || []).map(c => c.columnName)
  }, [sample, columns])

  async function loadTables() {
    if (!canCall) {
      setErr('Enter an Admin API key to load tables.')
      setTables([])
      setLoading(false)
      return
    }
    setLoading(true)
    setErr('')
    try {
      setAdminApiKey(apiKey)
      const res = await http.get('/api/admin/db/tables')
      setTables(res.data || [])
    } catch (e) {
      const code = e?.response?.status
      setErr(code === 401 ? '401 Unauthorized. Check Admin API key.' : (e?.message || 'Failed to load tables.'))
    } finally {
      setLoading(false)
    }
  }

  async function refreshOne(schema, table) {
    setErr('')
    try {
      setAdminApiKey(apiKey)
      const res = await http.get(`/api/admin/db/tables/${encodeURIComponent(schema)}/${encodeURIComponent(table)}`)
      const updated = res.data
      setTables((prev) =>
        prev.map((t) =>
          t.schemaName === schema && t.tableName === table ? updated : t
        )
      )
    } catch (e) {
      setErr(e?.message || 'Failed to refresh table stats.')
    }
  }

  async function loadDetails(schema, table) {
    setDetailLoading(true)
    setErr('')
    try {
      setAdminApiKey(apiKey)
      const [cRes, iRes, sRes] = await Promise.all([
        http.get(`/api/admin/db/tables/${encodeURIComponent(schema)}/${encodeURIComponent(table)}/columns`),
        http.get(`/api/admin/db/tables/${encodeURIComponent(schema)}/${encodeURIComponent(table)}/indexes`),
        http.get(`/api/admin/db/tables/${encodeURIComponent(schema)}/${encodeURIComponent(table)}/sample?limit=20`)
      ])
      setColumns(cRes.data || [])
      setIndexes(iRes.data || [])
      setSample(sRes.data || null)
    } catch (e) {
      setErr(e?.message || 'Failed to load table details.')
      setColumns([])
      setIndexes([])
      setSample(null)
    } finally {
      setDetailLoading(false)
    }
  }

  useEffect(() => {
    loadTables()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [canCall])

  async function toggleOpen(t) {
    const key = `${t.schemaName}.${t.tableName}`
    if (openKey === key) {
      setOpenKey(null)
      setColumns([])
      setIndexes([])
      setSample(null)
      return
    }
    setOpenKey(key)
    await loadDetails(t.schemaName, t.tableName)
  }

  return (
    <div className="space-y-5">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-xl font-semibold text-slate-900">Database</h1>
          <p className="mt-1 text-sm text-slate-600">
            Table sizes, row estimates, schema columns, indexes, and a sample of recent rows.
          </p>
        </div>

        <div className="w-full sm:w-[420px]">
          <label className="block text-sm font-medium text-slate-700">Admin API Key</label>
          <div className="mt-1 flex gap-2">
            <input
              value={apiKey}
              onChange={(e) => setApiKey(e.target.value)}
              placeholder="Paste FDP_ADMIN_API_KEY…"
              className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900"
            />
            <button
              onClick={() => loadTables()}
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
                <th className="px-4 py-3">Schema</th>
                <th className="px-4 py-3">Table</th>
                <th className="px-4 py-3">Rows (est)</th>
                <th className="px-4 py-3">Size</th>
                <th className="px-4 py-3">Vacuum/Analyze</th>
                <th className="px-4 py-3"></th>
              </tr>
            </thead>

            <tbody className="divide-y divide-slate-200">
              {loading ? (
                <tr><td className="px-4 py-4 text-slate-600" colSpan={6}>Loading…</td></tr>
              ) : tables.length === 0 ? (
                <tr><td className="px-4 py-4 text-slate-600" colSpan={6}>No FDP tables found.</td></tr>
              ) : (
                tables.map((t) => {
                  const key = `${t.schemaName}.${t.tableName}`
                  const isOpen = openKey === key
                  const vac = t.lastAutovacuum || t.lastVacuum
                  const ana = t.lastAutoanalyze || t.lastAnalyze

                  return (
                    <>
                      <tr
                        key={key}
                        className="cursor-pointer hover:bg-slate-50"
                        onClick={() => toggleOpen(t)}
                        title="Click to inspect"
                      >
                        <td className="px-4 py-3 font-mono text-xs text-slate-700">{t.schemaName}</td>
                        <td className="px-4 py-3 font-medium text-slate-900">{t.tableName}</td>
                        <td className="px-4 py-3 text-slate-700">{t.rowCountEstimate}</td>
                        <td className="px-4 py-3">
                          <span className="inline-flex items-center rounded-full border border-slate-200 bg-slate-50 px-2 py-0.5 text-xs font-semibold text-slate-800">
                            {bytesBadge(t.totalBytes, t.totalSizePretty)}
                          </span>
                        </td>
                        <td className="px-4 py-3 text-xs text-slate-600">
                          <div>vac: {prettyTime(vac)}</div>
                          <div>ana: {prettyTime(ana)}</div>
                        </td>
                        <td className="px-4 py-3 text-right">
                          <button
                            className="rounded-lg border border-slate-200 bg-white px-3 py-2 text-xs font-semibold text-slate-800 hover:bg-slate-50"
                            onClick={(e) => { e.stopPropagation(); refreshOne(t.schemaName, t.tableName) }}
                          >
                            Refresh
                          </button>
                        </td>
                      </tr>

                      {isOpen ? (
                        <tr key={key + ':open'}>
                          <td colSpan={6} className="bg-slate-50 px-4 py-4">
                            {detailLoading ? (
                              <div className="text-sm text-slate-600">Loading table details…</div>
                            ) : (
                              <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
                                {/* Columns */}
                                <div className="rounded-2xl border border-slate-200 bg-white p-4">
                                  <div className="text-sm font-semibold text-slate-900">Columns</div>
                                  <div className="mt-3 space-y-2 text-xs">
                                    {columns.length === 0 ? (
                                      <div className="text-slate-600">No columns.</div>
                                    ) : columns.map((c) => (
                                      <div key={c.columnName} className="rounded-lg border border-slate-100 bg-slate-50 p-2">
                                        <div className="flex items-center justify-between">
                                          <span className="font-mono text-slate-900">{c.columnName}</span>
                                          <span className="text-slate-600">{c.dataType}</span>
                                        </div>
                                        <div className="mt-1 text-slate-600">
                                          {c.nullable ? 'NULL' : 'NOT NULL'}
                                          {c.columnDefault ? ` • default: ${c.columnDefault}` : ''}
                                        </div>
                                      </div>
                                    ))}
                                  </div>
                                </div>

                                {/* Indexes */}
                                <div className="rounded-2xl border border-slate-200 bg-white p-4">
                                  <div className="text-sm font-semibold text-slate-900">Indexes</div>
                                  <div className="mt-3 space-y-2 text-xs">
                                    {indexes.length === 0 ? (
                                      <div className="text-slate-600">No indexes.</div>
                                    ) : indexes.map((ix) => (
                                      <div key={ix.indexName} className="rounded-lg border border-slate-100 bg-slate-50 p-2">
                                        <div className="flex items-center justify-between">
                                          <span className="font-mono text-slate-900">{ix.indexName}</span>
                                          <span className="text-slate-600">{ix.unique ? 'UNIQUE' : 'NON-UNIQUE'}</span>
                                        </div>
                                        <div className="mt-1 text-slate-600">
                                          cols: {Array.isArray(ix.columns) ? ix.columns.join(', ') : '—'}
                                        </div>
                                      </div>
                                    ))}
                                  </div>
                                </div>

                                {/* Sample */}
                                <div className="rounded-2xl border border-slate-200 bg-white p-4">
                                  <div className="flex items-center justify-between">
                                    <div className="text-sm font-semibold text-slate-900">Sample rows</div>
                                    <div className="text-xs text-slate-600">
                                      order: {sample?.primaryKeyColumns?.length ? `${sample.primaryKeyColumns[0]} DESC` : 'ctid DESC'}
                                    </div>
                                  </div>

                                  <div className="mt-3 overflow-auto rounded-xl border border-slate-200 bg-white">
                                    <table className="min-w-full text-left text-xs">
                                      <thead className="bg-slate-50 text-[10px] font-semibold uppercase tracking-wide text-slate-600">
                                        <tr>
                                          {sampleHeaders.map((k) => (
                                            <th key={k} className="px-3 py-2">{k}</th>
                                          ))}
                                        </tr>
                                      </thead>
                                      <tbody className="divide-y divide-slate-200">
                                        {(sample?.rows || []).length === 0 ? (
                                          <tr><td className="px-3 py-3 text-slate-600" colSpan={sampleHeaders.length || 1}>No rows.</td></tr>
                                        ) : (
                                          sample.rows.map((row, idx) => (
                                            <tr key={idx} className="align-top">
                                              {sampleHeaders.map((k) => {
                                                const v = row?.[k]
                                                const json = tryFormatJson(v)
                                                return (
                                                  <td key={k} className="px-3 py-2 text-slate-800">
                                                    {json ? (
                                                      <pre className="max-w-[320px] overflow-auto rounded-lg bg-slate-50 p-2 text-[11px] text-slate-800">
{json}
                                                      </pre>
                                                    ) : (
                                                      <span className="whitespace-pre-wrap break-words">
                                                        {v == null ? '—' : String(v)}
                                                      </span>
                                                    )}
                                                  </td>
                                                )
                                              })}
                                            </tr>
                                          ))
                                        )}
                                      </tbody>
                                    </table>
                                  </div>
                                </div>
                              </div>
                            )}
                          </td>
                        </tr>
                      ) : null}
                    </>
                  )
                })
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  )
}