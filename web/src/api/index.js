import axios from 'axios'

const API_BASE_URL = '/api'

const api = axios.create({
  baseURL: API_BASE_URL,
  timeout: 10000
})

api.interceptors.response.use(
  response => response.data,
  error => {
    console.error('API error:', error)
    throw error
  }
)

export const deviceApi = {
  getAll: () => api.get('/devices'),
  getById: (id) => api.get(`/devices/${id}`),
  create: (device) => api.post('/devices', device),
  update: (id, device) => api.put(`/devices/${id}`, device),
  delete: (id) => api.delete(`/devices/${id}`),
  start: (id) => api.post(`/devices/${id}/start`),
  stop: (id) => api.post(`/devices/${id}/stop`),
  report: (id) => api.post(`/devices/${id}/report`),
  setInterval: (id, interval) => api.post(`/devices/${id}/setInterval`, { interval })
}

export const dataApi = {
  getDeviceData: (deviceId) => api.get(`/devices/${deviceId}/data`),
  getLatest: () => api.get('/data/latest'),
  getStatistics: () => api.get('/data/statistics')
}

export const alertApi = {
  getAll: () => api.get('/alerts'),
  toggle: () => api.post('/alerts/toggle'),
  manual: (message) => api.post('/alerts/manual', { message })
}