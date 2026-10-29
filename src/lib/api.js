// Use a valid UUID for testing (in production, get from auth context)
const TEST_USER_ID = '00000000-0000-0000-0000-000000000001'
const API_BASE = import.meta.env.VITE_API_URL || 'http://localhost:8000'
const FETCH_TIMEOUT = 15000 // 15 seconds

async function request(endpoint, options = {}) {
  const controller = new AbortController()
  const timeoutId = setTimeout(() => controller.abort(), FETCH_TIMEOUT)

  try {
    const response = await fetch(`${API_BASE}${endpoint}`, {
      headers: {
        'Content-Type': 'application/json',
        'X-User-ID': TEST_USER_ID,
        ...options.headers,
      },
      signal: controller.signal,
      ...options,
    })

    clearTimeout(timeoutId)

    if (!response.ok) {
      const error = await response.json().catch(() => ({ detail: 'Unknown error' }))
      throw new Error(error.detail || `HTTP ${response.status}`)
    }

    return response.json()
  } catch (err) {
    clearTimeout(timeoutId)
    if (err.name === 'AbortError') {
      throw new Error('Request timeout - server took too long to respond')
    }
    throw err
  }
}

export { TEST_USER_ID }
export const api = {
  // Health
  health: () => request('/api/health'),

  // Schedule
  scheduleGenerate: (data) => request('/api/schedule/generate', {
    method: 'POST',
    body: JSON.stringify(data),
  }),

  scheduleAccept: (data) => request('/api/schedule/accept', {
    method: 'POST',
    body: JSON.stringify(data),
  }),

  scheduleItems: (userId) => request(`/api/schedule/items`, {
    headers: { 'X-User-ID': userId },
  }),

  scheduleFixedCreate: (data) => request('/api/schedule/fixed', {
    method: 'POST',
    body: JSON.stringify(data),
  }),

  scheduleFixedDelete: (itemId, userId) => request(`/api/schedule/fixed`, {
    method: 'DELETE',
    headers: { 'X-User-ID': userId },
    body: JSON.stringify({ id: itemId, user_id: userId }),
  }),

  // Repair
  repairPropose: (data) => request('/api/repair/propose', {
    method: 'POST',
    body: JSON.stringify(data),
  }),

  repairApply: (data) => request('/api/repair/apply', {
    method: 'POST',
    body: JSON.stringify(data),
  }),

  // Behavior
  behaviorProfile: (userId) => request('/api/behavior/profile', {
    headers: { 'X-User-ID': userId },
  }),

  behaviorUpdate: (data) => request('/api/behavior/update', {
    method: 'POST',
    body: JSON.stringify(data),
  }),

  onboarding: (data) => request('/api/onboarding', {
    method: 'POST',
    body: JSON.stringify(data),
  }),

  // Command
  command: (data) => request('/api/command', {
    method: 'POST',
    body: JSON.stringify(data),
  }),

  // Events (Chrona parser)
  processEvent: (data) => request('/api/process-event', {
    method: 'POST',
    body: JSON.stringify(data),
  }),
}