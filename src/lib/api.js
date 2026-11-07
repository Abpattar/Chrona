/**
 * Chrona API Client — v2
 * Connects to the Python FastAPI backend at VITE_API_URL (default: http://localhost:8000)
 */

// Default user ID for single-user mode (no auth yet)
export const TEST_USER_ID = '00000000-0000-0000-0000-000000000001'

export function getClientTimezone() {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || 'Asia/Kolkata'
  } catch {
    return 'Asia/Kolkata'
  }
}

export function getClientTimezoneOffset() {
  try {
    return String(-new Date().getTimezoneOffset()) // in minutes, e.g. "330" for IST
  } catch {
    return '330'
  }
}

const API_BASE = import.meta.env.VITE_API_URL ||
  (import.meta.env.PROD ? '/api' : 'http://localhost:8000')

const TIMEOUT = 20000  // 20 seconds for AI calls

async function request(endpoint, options = {}) {
  const controller = new AbortController()
  const timeoutId = setTimeout(() => controller.abort(), TIMEOUT)

  try {
    const response = await fetch(`${API_BASE}${endpoint}`, {
      headers: {
        'Content-Type': 'application/json',
        'X-User-ID': TEST_USER_ID,
        'X-Timezone': getClientTimezone(),
        'X-Timezone-Offset': getClientTimezoneOffset(),
        ...options.headers,
      },
      signal: controller.signal,
      ...options,
    })

    clearTimeout(timeoutId)

    if (!response.ok) {
      const err = await response.json().catch(() => ({ detail: `HTTP ${response.status}` }))
      throw new Error(err.detail || `HTTP ${response.status}`)
    }

    return response.json()
  } catch (err) {
    clearTimeout(timeoutId)
    if (err.name === 'AbortError') throw new Error('Request timed out — server is busy')
    throw err
  }
}

/** Multipart file upload — no Content-Type override (let browser set boundary) */
async function uploadRequest(endpoint, formData) {
  const controller = new AbortController()
  const timeoutId = setTimeout(() => controller.abort(), TIMEOUT)

  try {
    const response = await fetch(`${API_BASE}${endpoint}`, {
      method: 'POST',
      headers: {
        'X-User-ID': TEST_USER_ID,
        'X-Timezone': getClientTimezone(),
        'X-Timezone-Offset': getClientTimezoneOffset(),
      },
      body: formData,
      signal: controller.signal,
    })
    clearTimeout(timeoutId)
    if (!response.ok) {
      const err = await response.json().catch(() => ({ detail: `HTTP ${response.status}` }))
      throw new Error(err.detail || `HTTP ${response.status}`)
    }
    return response.json()
  } catch (err) {
    clearTimeout(timeoutId)
    if (err.name === 'AbortError') throw new Error('Upload timed out')
    throw err
  }
}

export const api = {
  // ── Health ──────────────────────────────────────────────
  health: () => request('/api/health'),

  // ── Onboarding ──────────────────────────────────────────
  onboarding: (data) => request('/api/onboarding', {
    method: 'POST',
    body: JSON.stringify({ user_id: TEST_USER_ID, ...data }),
  }),

  // ── Chat / Natural Language ──────────────────────────────
  chat: (message, sessionId = null, context = null) => request('/api/chat', {
    method: 'POST',
    body: JSON.stringify({
      user_id: TEST_USER_ID,
      message,
      session_id: sessionId,
      timezone: getClientTimezone(),
      timezone_offset: getClientTimezoneOffset(),
      client_time: new Date().toISOString(),
      context,
    }),
  }),

  // ── Process Event (file/text extraction) ─────────────────
  processEvent: (textOrFormData) => {
    if (typeof textOrFormData === 'string') {
      return request('/api/process-event', {
        method: 'POST',
        body: JSON.stringify({ text: textOrFormData }),
      })
    }
    return uploadRequest('/api/process-event', textOrFormData)
  },

  // ── Task Decomposition ────────────────────────────────────
  decompose: (title, description, deadline, priority = 3) => request('/api/decompose', {
    method: 'POST',
    body: JSON.stringify({
      user_id: TEST_USER_ID,
      title,
      description,
      deadline,
      priority,
    }),
  }),

  // ── Schedule ─────────────────────────────────────────────
  scheduleGenerate: (startDate = null, endDate = null, force = false) =>
    request('/api/schedule/generate', {
      method: 'POST',
      body: JSON.stringify({
        user_id: TEST_USER_ID,
        start_date: startDate,
        end_date: endDate,
        force,
      }),
    }),

  scheduleAccept: (version, scheduledItems) => request('/api/schedule/accept', {
    method: 'POST',
    body: JSON.stringify({
      user_id: TEST_USER_ID,
      version,
      scheduled_items: scheduledItems,
    }),
  }),

  scheduleItems: (start = null, end = null, status = null) => {
    const params = new URLSearchParams()
    if (start)  params.set('start', start)
    if (end)    params.set('end', end)
    if (status) params.set('status', status)
    const qs = params.toString()
    return request(`/api/schedule/items${qs ? '?' + qs : ''}`)
  },

  scheduleFixedCreate: (data) => request('/api/schedule/fixed', {
    method: 'POST',
    body: JSON.stringify({ user_id: TEST_USER_ID, ...data }),
  }),

  overrideBreak: (data) => request('/api/schedule/override-break', {
    method: 'POST',
    body: JSON.stringify({ user_id: TEST_USER_ID, ...data }),
  }),

  explainSchedule: (taskId) => request(`/api/schedule/explain/${taskId}`),

  executeAgent: (taskId, action = null, instructions = null) => request('/api/agents/execute', {
    method: 'POST',
    body: JSON.stringify({
      user_id: TEST_USER_ID,
      task_id: taskId,
      action,
      instructions,
    }),
  }),

  scheduleFixedDelete: (itemId) => request(`/api/schedule/fixed/${itemId}`, {
    method: 'DELETE',
  }),

  updateScheduleItem: (itemId, data) => request(`/api/schedule/items/${itemId}`, {
    method: 'PATCH',
    body: JSON.stringify(data),
  }),

  freeSlots: (start = null, end = null) => {
    const params = new URLSearchParams()
    if (start) params.set('start', start)
    if (end)   params.set('end', end)
    const qs = params.toString()
    return request(`/api/schedule/free-slots${qs ? '?' + qs : ''}`)
  },

  // ── Tasks ─────────────────────────────────────────────────
  completeTask: (taskId, actualDurationMinutes = null, note = null) =>
    request('/api/tasks/complete', {
      method: 'POST',
      body: JSON.stringify({
        user_id: TEST_USER_ID,
        task_id: taskId,
        actual_duration_minutes: actualDurationMinutes,
        note,
      }),
    }),

  skipTask: (taskId, note = null) => request('/api/tasks/skip', {
    method: 'POST',
    body: JSON.stringify({
      user_id: TEST_USER_ID,
      task_id: taskId,
      note,
    }),
  }),

  // ── Auto Reschedule ────────────────────────────────────────
  autoReschedule: (taskId = null, allUnplaced = false) =>
    request('/api/schedule/auto-reschedule', {
      method: 'POST',
      body: JSON.stringify({
        user_id: TEST_USER_ID,
        task_id: taskId,
        all_unplaced: allUnplaced,
      }),
    }),

  // ── Chat with Upload ──────────────────────────────────────
  chatUpload: (formData) => uploadRequest('/api/chat/upload', formData),

  // ── Repair ───────────────────────────────────────────────
  repairPropose: (disruptedTaskId, reason = null) => request('/api/repair/propose', {
    method: 'POST',
    body: JSON.stringify({
      user_id: TEST_USER_ID,
      disrupted_task_id: disruptedTaskId,
      reason,
    }),
  }),

  repairApply: (proposal) => request('/api/repair/apply', {
    method: 'POST',
    body: JSON.stringify({
      user_id: TEST_USER_ID,
      proposal,
    }),
  }),

  // ── Behavior ──────────────────────────────────────────────
  behaviorProfile: () => request('/api/behavior/profile'),

  behaviorUpdate: (taskId, signal, actualStart = null, metadata = null) =>
    request('/api/behavior/update', {
      method: 'POST',
      body: JSON.stringify({
        user_id: TEST_USER_ID,
        task_id: taskId,
        signal,
        actual_start: actualStart,
        metadata,
      }),
    }),

  // ── Context ───────────────────────────────────────────────
  context: () => request('/api/context'),

  // ── Projects ──────────────────────────────────────────────
  projects: () => request('/api/projects'),

  // ── Events ────────────────────────────────────────────────
  events: () => request('/api/events'),

  // ── Legacy command ────────────────────────────────────────
  command: (command) => request('/api/command', {
    method: 'POST',
    body: JSON.stringify({ user_id: TEST_USER_ID, command }),
  }),
}
