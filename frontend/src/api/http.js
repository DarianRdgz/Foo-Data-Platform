import axios from 'axios'

const KEY_STORAGE = 'fdp_admin_api_key'

export function getAdminApiKey() {
  return localStorage.getItem(KEY_STORAGE) || ''
}

export function setAdminApiKey(key) {
  const v = (key || '').trim()
  if (!v) localStorage.removeItem(KEY_STORAGE)
  else localStorage.setItem(KEY_STORAGE, v)
}

export const http = axios.create({
  baseURL: '/',
  timeout: 30000,
})

http.interceptors.request.use((config) => {
  const key = getAdminApiKey()
  if (key) {
    config.headers = config.headers || {}
    config.headers['X-API-KEY'] = key
  }
  return config
})