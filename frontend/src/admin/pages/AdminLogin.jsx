import { useMemo, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { setAdminAuthed } from '../auth'

export default function AdminLogin() {
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const next = params.get('next') || '/admin'

  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')

  const expectedUser = useMemo(() => import.meta.env.VITE_ADMIN_USER || '', [])
  const expectedPass = useMemo(() => import.meta.env.VITE_ADMIN_PASS || '', [])

  function submit(e) {
    e.preventDefault()
    setError('')

    if (!expectedUser || !expectedPass) {
      setError('Missing VITE_ADMIN_USER / VITE_ADMIN_PASS in frontend/.env.local')
      return
    }

    if (username !== expectedUser || password !== expectedPass) {
      setError('Invalid credentials.')
      return
    }

    setAdminAuthed(true)
    navigate(next, { replace: true })
  }

  return (
    <div className="min-h-full bg-slate-50">
      <div className="mx-auto flex min-h-screen max-w-md items-center px-4">
        <div className="w-full rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
          <div className="mb-6">
            <div className="text-xs font-semibold tracking-wide text-slate-500">
              Foo Data Platform
            </div>
            <h1 className="text-xl font-semibold text-slate-900">Admin Login</h1>
          </div>

          <form onSubmit={submit} className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-slate-700">
                Username
              </label>
              <input
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900"
                autoComplete="username"
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-slate-700">
                Password
              </label>
              <input
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900"
                autoComplete="current-password"
              />
            </div>

            {error ? (
              <div className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-800">
                {error}
              </div>
            ) : null}

            <button
              type="submit"
              className="w-full rounded-lg bg-slate-900 px-3 py-2 text-sm font-semibold text-white hover:bg-slate-800"
            >
              Sign in
            </button>

            <div className="text-xs text-slate-500">
              Dev-only route gate for Sprint 4.1.
            </div>
          </form>
        </div>
      </div>
    </div>
  )
}