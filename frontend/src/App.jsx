import { Navigate, Route, Routes } from 'react-router-dom'
import RequireAdmin from './admin/RequireAdmin'
import AdminLayout from './admin/AdminLayout'
import AdminLogin from './admin/pages/AdminLogin'
import AdminOverview from './admin/pages/AdminOverview'
import AdminJobs from './admin/pages/AdminJobs'
import AdminIngestion from './admin/pages/AdminIngestion'
import AdminDb from './admin/pages/AdminDb'

function PublicHome() {
  return (
    <div className="min-h-full bg-slate-50">
      <div className="mx-auto max-w-3xl px-4 py-10">
        <h1 className="text-2xl font-semibold text-slate-900">FDP</h1>
        <p className="mt-2 text-sm text-slate-600">
          Open <a className="underline" href="/admin">/admin</a> for the admin console.
        </p>
      </div>
    </div>
  )
}

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<PublicHome />} />

      <Route path="/admin/login" element={<AdminLogin />} />

      <Route
        path="/admin"
        element={
          <RequireAdmin>
            <AdminLayout />
          </RequireAdmin>
        }
      >
        <Route index element={<AdminOverview />} />
        <Route path="jobs" element={<AdminJobs />} />
        <Route path="ingestion" element={<AdminIngestion />} />
        <Route path="db" element={<AdminDb />} />
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}