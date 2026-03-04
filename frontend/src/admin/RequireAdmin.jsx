import { Navigate, useLocation } from 'react-router-dom'
import { isAdminAuthed } from './auth'

export default function RequireAdmin({ children }) {
  const location = useLocation()

  if (!isAdminAuthed()) {
    const next = encodeURIComponent(location.pathname + location.search)
    return <Navigate to={`/admin/login?next=${next}`} replace />
  }

  return children
}