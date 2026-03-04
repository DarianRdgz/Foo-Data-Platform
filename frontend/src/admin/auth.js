const STORAGE_KEY = 'fdp_admin_authed_v1'

export function isAdminAuthed() {
  return localStorage.getItem(STORAGE_KEY) === 'true'
}

export function setAdminAuthed(value) {
  localStorage.setItem(STORAGE_KEY, value ? 'true' : 'false')
}

export function clearAdminAuthed() {
  localStorage.removeItem(STORAGE_KEY)
}