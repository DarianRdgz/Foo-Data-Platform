import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { clearAdminAuthed } from './auth'

function NavItem({ to, children }) {
  return (
    <NavLink
      to={to}
      end={to === '/admin'}
      className={({ isActive }) =>
        [
          'block rounded-lg px-3 py-2 text-sm font-medium',
          isActive
            ? 'bg-slate-900 text-white'
            : 'text-slate-700 hover:bg-slate-100 hover:text-slate-900',
        ].join(' ')
      }
    >
      {children}
    </NavLink>
  )
}

export default function AdminLayout() {
  const navigate = useNavigate()

  function logout() {
    clearAdminAuthed()
    navigate('/admin/login', { replace: true })
  }

  return (
    <div className="min-h-full bg-slate-50">
      <div className="mx-auto max-w-screen-2xl px-4 py-6">
        <div className="grid grid-cols-12 gap-6">
          <aside className="col-span-12 xl:col-span-3">
            <div className="sticky top-6 rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
              <div className="mb-4">
                <div className="text-xs font-semibold tracking-wide text-slate-500">
                  Foo Data Platform
                </div>
                <div className="text-lg font-semibold text-slate-900">Admin</div>
              </div>

              <nav className="space-y-1">
                <NavItem to="/admin">Overview</NavItem>
                <NavItem to="/admin/jobs">Jobs</NavItem>
                <NavItem to="/admin/ingestion">Ingestion</NavItem>
                <NavItem to="/admin/db">Database</NavItem>
              </nav>

              <div className="mt-6 border-t border-slate-200 pt-4">
                <button
                  onClick={logout}
                  className="w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50"
                >
                  Log out
                </button>
              </div>
            </div>
          </aside>

          <main className="col-span-12 xl:col-span-9">
            <div className="rounded-2xl border border-slate-200 bg-white shadow-sm">
              <header className="flex items-center justify-between border-b border-slate-200 px-5 py-4">
                <div>
                  <div className="text-sm text-slate-500">Admin Console</div>
                  <div className="text-base font-semibold text-slate-900">
                    Operations
                  </div>
                </div>

                <a
                  href="/"
                  className="rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50"
                >
                  Back to app
                </a>
              </header>

              <div className="p-5">
                <Outlet />
              </div>
            </div>
          </main>
        </div>
      </div>
    </div>
  )
}