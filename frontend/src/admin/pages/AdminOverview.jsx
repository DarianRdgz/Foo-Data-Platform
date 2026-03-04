import { useEffect, useState } from 'react'

export default function AdminOverview() {
  const [health, setHealth] = useState('loading...')

  useEffect(() => {
    fetch('/api/health')
      .then((r) => r.text())
      .then(setHealth)
      .catch(() => setHealth('error'))
  }, [])

  return (
    <div className="space-y-4">
      <h1 className="text-xl font-semibold text-slate-900">Overview</h1>

      <div className="rounded-xl border border-slate-200 bg-slate-50 p-4">
        <div className="text-sm text-slate-600">Backend health</div>
        <div className="mt-1 font-mono text-sm text-slate-900">{health}</div>
      </div>
    </div>
  )
}