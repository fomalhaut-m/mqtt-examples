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
  getLatest: () => api.get('/data/latest'),
  getLatestByDevice: (deviceId) => api.get(`/data/latest/${deviceId}`),
  getHistory: (deviceId, params) => api.get(`/data/history/${deviceId}`, { params }),
  getStatistics: () => api.get('/data/statistics')
}

export const alertApi = {
  getAll: () => api.get('/alerts'),
  getStatus: () => api.get('/alerts/status'),
  toggle: () => api.post('/alerts/toggle'),
  manual: (message) => api.post('/alerts/manual', { message })
}

export const sseApi = {
  getSseUrl: () => '/sse/data',
  getSseStatus: () => axios.get('/sse/status').then(r => r.data),
  getSystemSnapshot: () => axios.get('/sse/system/snapshot').then(r => r.data),
  getLanSnapshot: () => axios.get('/sse/lan/snapshot').then(r => r.data)
}