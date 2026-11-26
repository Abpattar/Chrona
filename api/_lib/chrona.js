import { createClient } from '@supabase/supabase-js'
import { GoogleGenerativeAI } from '@google/generative-ai'

export const DEFAULT_USER_ID = '00000000-0000-0000-0000-000000000001'

const GEMINI_MODELS = [
  'gemini-flash-lite-latest',
  'gemini-3-flash-preview',
  'gemini-3.5-flash-lite',
  'gemini-3.5-flash',
]

export function getSupabase() {
  const url = process.env.SUPABASE_URL
  const key = process.env.SUPABASE_SERVICE_KEY || process.env.SUPABASE_ANON_KEY
  if (!url || !key) return null
  try {
    return createClient(url, key)
  } catch (e) {
    console.warn('Could not initialize Supabase:', e.message)
    return null
  }
}

export function setCors(res, methods = 'GET, POST, PATCH, DELETE, OPTIONS') {
  res.setHeader('Access-Control-Allow-Origin', '*')
  res.setHeader('Access-Control-Allow-Methods', methods)
  res.setHeader(
    'Access-Control-Allow-Headers',
    'Content-Type, X-User-ID, X-Timezone, X-Timezone-Offset'
  )
}

export function getUserId(req) {
  return req.headers['x-user-id'] || DEFAULT_USER_ID
}

export function getTimezoneContext(req, body = null) {
  const tzName =
    (body && body.timezone) ||
    req.headers['x-timezone'] ||
    'Asia/Kolkata'

  let offsetMinutes = null
  const rawOffset = (body && body.timezone_offset) || req.headers['x-timezone-offset']
  if (rawOffset !== undefined && rawOffset !== null && rawOffset !== '') {
    const parsed = Number(rawOffset)
    if (!Number.isNaN(parsed) && Math.abs(parsed) <= 840) {
      offsetMinutes = parsed
    }
  }

  const now = new Date()
  if (offsetMinutes === null) {
    try {
      const parts = new Intl.DateTimeFormat('en-US', {
        timeZone: tzName,
        timeZoneName: 'shortOffset',
      }).formatToParts(now)
      const tzPart = parts.find((p) => p.type === 'timeZoneName')?.value || 'GMT+5:30'
      const m = tzPart.match(/GMT([+-])(\d{1,2})(?::?(\d{2}))?/)
      if (m) {
        const sign = m[1] === '-' ? -1 : 1
        offsetMinutes = sign * (parseInt(m[2], 10) * 60 + parseInt(m[3] || '0', 10))
      }
    } catch {
      offsetMinutes = 330
    }
  }
  if (offsetMinutes === null) offsetMinutes = 330

  const sign = offsetMinutes >= 0 ? '+' : '-'
  const absMin = Math.abs(offsetMinutes)
  const hh = String(Math.floor(absMin / 60)).padStart(2, '0')
  const mm = String(absMin % 60).padStart(2, '0')
  const offsetStr = `${sign}${hh}:${mm}`

  const localMs = now.getTime() + offsetMinutes * 60 * 1000
  const localDate = new Date(localMs)
  const localIso = localDate.toISOString().slice(0, 19) + offsetStr
  const localDateStr = localDate.toISOString().slice(0, 10)
  const weekday = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'][
    localDate.getUTCDay()
  ]

  return {
    tzName,
    offsetMinutes,
    offsetStr,
    now,
    localDateStr,
    localIso,
    weekday,
    nowDisplay: `${localDateStr} (${weekday}) ${localDate.toISOString().slice(11, 16)} ${offsetStr}`,
  }
}

function stripJsonFences(text) {
  let clean = (text || '').trim()
  if (clean.startsWith('```')) {
    clean = clean.replace(/^```(?:json)?\s*/i, '').replace(/\s*```$/, '')
  }
  return clean.trim()
}

export async function callGemini({ parts, jsonMode = false, perModelTimeoutMs = 15000 }) {
  const apiKey = (process.env.GEMINI_API_KEY || '').trim()
  if (!apiKey) {
    throw new Error('GEMINI_API_KEY is not configured on the server.')
  }
  const genAI = new GoogleGenerativeAI(apiKey)
  const errors = []

  for (const modelName of GEMINI_MODELS) {
    try {
      const model = genAI.getGenerativeModel({
        model: modelName,
        generationConfig: {
          responseMimeType: jsonMode ? 'application/json' : 'text/plain',
          temperature: 0.15,
          maxOutputTokens: 4096,
        },
      })

      const result = await Promise.race([
        model.generateContent(parts),
        new Promise((_, reject) =>
          setTimeout(() => reject(new Error(`${modelName} timeout`)), perModelTimeoutMs)
        ),
      ])

      const text = result?.response?.text?.() || ''
      if (!text.trim()) {
        errors.push(`${modelName}: empty response`)
        continue
      }

      if (jsonMode) {
        return JSON.parse(stripJsonFences(text))
      }
      return text.trim()
    } catch (err) {
      errors.push(`${modelName}: ${err.message}`)
    }
  }

  throw new Error(`Gemini request failed (${errors.slice(-2).join(' | ')})`)
}

export function buildExtractionPrompt(tzCtx, userNote = '') {
  return `You are Chrona, an intelligent AI time-management copilot and schedule extraction engine.

Current user local date & time: ${tzCtx.nowDisplay} (Timezone: ${tzCtx.tzName}, offset ${tzCtx.offsetStr})
${userNote ? `User message / instructions: ${userNote}\n` : ''}
Analyze the provided input (image, PDF, document, or message) and extract ALL events, classes, exams, deadlines, appointments, routines, or tasks.
If the user is just chatting or asking a question with no schedulable tasks, return an empty "tasks" array and provide a helpful conversational "message".

Return ONLY valid JSON with this exact structure:
{
  "message": "Concise, helpful assistant reply summarizing what was extracted/scheduled or answering the user.",
  "tasks": [
    {
      "title": "Clear concise title",
      "category": "exam|quiz|assignment|class|meeting|study|workout|family|social|appointment|personal|reminder|project|other",
      "task_type": "exam|quiz|assignment|class|meeting|study|workout|family|social|appointment|task",
      "venue": "string or null",
      "start_time": "ISO 8601 datetime with offset ${tzCtx.offsetStr} (e.g. ${tzCtx.localDateStr}T17:00:00${tzCtx.offsetStr}) or null if flexible/unspecified",
      "end_time": "ISO 8601 datetime with offset ${tzCtx.offsetStr} or null",
      "deadline": "ISO 8601 datetime with offset ${tzCtx.offsetStr} or null",
      "duration_minutes": 60,
      "priority": 2,
      "movability": "immovable|negotiable|fluid",
      "transition_reason": "Brief realistic transition/prep buffer note if relevant, else null"
    }
  ],
  "clarification_needed": null
}

Rules:
- Convert relative dates ("today", "tomorrow", "next Monday", etc.) using current local date ${tzCtx.localDateStr} (${tzCtx.weekday}).
- Always include timezone offset ${tzCtx.offsetStr} on all ISO datetimes.
- If a start_time is known but end_time isn't stated, compute end_time using realistic duration_minutes (e.g. 60 for meetings/classes, 120-180 for exams, 30 for quick calls).
- movability: "immovable" for exams, classes, fixed meetings, appointments; "negotiable" for personal commitments; "fluid" for self-study, assignments, gym, chores.
- priority: 1 = critical/exam/urgent, 2 = high/fixed commitment, 3 = normal, 4 = low.`
}

function normalizeIso(dtStr, offsetStr) {
  if (!dtStr || typeof dtStr !== 'string') return null
  const trimmed = dtStr.trim()
  if (!trimmed) return null
  // If no timezone offset or Z at end, append offsetStr
  const hasTz = /(?:Z|[+-]\d{2}:?\d{2})$/i.test(trimmed)
  const candidate = hasTz ? trimmed : `${trimmed}${offsetStr}`
  const parsed = new Date(candidate)
  if (Number.isNaN(parsed.getTime())) return null
  return parsed.toISOString()
}

function findNextOpenSlot(durationMin, existingItems, tzCtx) {
  const durMs = Math.max(15, durationMin || 60) * 60 * 1000
  const busy = existingItems
    .filter((it) => it.start_time && it.end_time && it.status !== 'cancelled')
    .map((it) => ({
      start: new Date(it.start_time).getTime(),
      end: new Date(it.end_time).getTime(),
    }))
    .filter((b) => !Number.isNaN(b.start) && !Number.isNaN(b.end))

  // Start checking from the next 30-minute boundary at least 15 mins from now
  let cursor = new Date(Math.ceil((tzCtx.now.getTime() + 15 * 60000) / (30 * 60000)) * (30 * 60000))

  for (let i = 0; i < 240; i++) {
    const localHour = new Date(cursor.getTime() + tzCtx.offsetMinutes * 60000).getUTCHours()
    if (localHour >= 8 && localHour < 22) {
      const slotStart = cursor.getTime()
      const slotEnd = slotStart + durMs
      const overlaps = busy.some((b) => slotStart < b.end && slotEnd > b.start)
      if (!overlaps) {
        return {
          startIso: new Date(slotStart).toISOString(),
          endIso: new Date(slotEnd).toISOString(),
        }
      }
    }
    cursor = new Date(cursor.getTime() + 30 * 60000)
  }

  return {
    startIso: cursor.toISOString(),
    endIso: new Date(cursor.getTime() + durMs).toISOString(),
  }
}

export function enrichScheduleItem(row) {
  const startMs = row.start_time ? new Date(row.start_time).getTime() : NaN
  const endMs = row.end_time ? new Date(row.end_time).getTime() : NaN
  const durationMinutes =
    !Number.isNaN(startMs) && !Number.isNaN(endMs) && endMs > startMs
      ? Math.round((endMs - startMs) / 60000)
      : row.duration_minutes || 60

  return {
    ...row,
    duration_minutes: durationMinutes,
    movability: row.movability || (row.flexibility === false ? 'immovable' : 'fluid'),
  }
}

const STOP_WORDS = new Set([
  'exam', 'test', 'quiz', 'class', 'lecture', 'session', 'meeting', 'task',
  'event', 'assignment', 'prep', 'study', 'the', 'for', 'in', 'on', 'at',
  'of', 'and', 'to', 'a', 'an', 'my', 'with',
])

function normalizeRawTitle(title) {
  return (title || '')
    .toLowerCase()
    .replace(/[^a-z0-9\s]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
}

function coreTitleTokens(title) {
  const withoutCodes = (title || '')
    .toLowerCase()
    .replace(/\b[a-z]{2,5}\s*\d{3,4}[a-z]?\b/gi, ' ')
    .replace(/[^a-z0-9\s]/g, ' ')
  const rawTokens = withoutCodes.split(/\s+/).filter(Boolean)
  const filtered = rawTokens.filter((w) => !STOP_WORDS.has(w))
  return [...new Set(filtered.length > 0 ? filtered : rawTokens)]
}

function isDuplicateCandidate(existing, candidateTitle, candidateStartIso, hasExplicitStart = true, offsetMinutes = 330) {
  if (!existing || existing.status === 'cancelled') return false
  const normA = normalizeRawTitle(existing.title)
  const normB = normalizeRawTitle(candidateTitle)
  if (!normA || !normB) return false

  const tokA = coreTitleTokens(existing.title)
  const tokB = coreTitleTokens(candidateTitle)
  const intersection = tokA.filter((t) => tokB.includes(t))
  const overlapMin = intersection.length / Math.max(1, Math.min(tokA.length, tokB.length))
  const overlapMax = intersection.length / Math.max(1, Math.max(tokA.length, tokB.length))

  let sameTime = false
  let sameDay = false
  let diffMin = Infinity
  if (existing.start_time && candidateStartIso) {
    const msA = new Date(existing.start_time).getTime()
    const msB = new Date(candidateStartIso).getTime()
    if (!Number.isNaN(msA) && !Number.isNaN(msB)) {
      diffMin = Math.abs(msA - msB) / 60000
      sameTime = diffMin <= 45
      const dayA = new Date(msA + offsetMinutes * 60000).toISOString().slice(0, 10)
      const dayB = new Date(msB + offsetMinutes * 60000).toISOString().slice(0, 10)
      sameDay = dayA === dayB
    }
  }

  if (normA === normB) {
    if (!hasExplicitStart || sameTime || sameDay || diffMin <= 24 * 60) {
      return true
    }
  }

  if (overlapMin >= 0.65 && overlapMax >= 0.45) {
    if (!hasExplicitStart || sameTime || sameDay) {
      return true
    }
  }

  return false
}

export function deduplicateScheduleItems(items, offsetMinutes = 330) {
  if (!Array.isArray(items)) return []
  const kept = []
  // Prefer richer titles (e.g., with course codes) when collapsing historical duplicates
  const ordered = [...items].sort((a, b) => (b.title || '').length - (a.title || '').length)
  for (const item of ordered) {
    if (!item?.title || item.status === 'cancelled') continue
    const dup = kept.find((k) =>
      isDuplicateCandidate(k, item.title, item.start_time, !!item.start_time, offsetMinutes)
    )
    if (!dup) kept.push(item)
  }
  return kept.sort((a, b) => new Date(a.start_time || 0) - new Date(b.start_time || 0))
}

export async function saveExtractedTasks(supabase, userId, tasks, tzCtx) {
  if (!Array.isArray(tasks) || tasks.length === 0) return { saved: [], repeated: [] }

  let existing = []
  if (supabase) {
    try {
      const { data } = await supabase
        .from('schedule_items')
        .select('*')
        .eq('user_id', userId)
        .neq('status', 'cancelled')
      if (Array.isArray(data)) existing = data
    } catch (err) {
      console.warn('Could not read existing schedule_items:', err.message)
    }
  }

  const saved = []
  const repeated = []
  const workingList = [...existing]

  for (const raw of tasks) {
    const title = (raw?.title || '').trim()
    if (!title) continue

    const durationMin = Math.max(15, Math.min(720, Number(raw.duration_minutes) || 60))
    let startIso = normalizeIso(raw.start_time, tzCtx.offsetStr)
    let endIso = normalizeIso(raw.end_time, tzCtx.offsetStr)
    const hasExplicitStart = Boolean(startIso)

    if (startIso && (!endIso || new Date(endIso).getTime() <= new Date(startIso).getTime())) {
      endIso = new Date(new Date(startIso).getTime() + durationMin * 60000).toISOString()
    }

    if (!startIso) {
      const slot = findNextOpenSlot(durationMin, workingList, tzCtx)
      startIso = slot.startIso
      endIso = slot.endIso
    }

    // Check against active (non-completed) items for repeated tasks
    const matchedExisting = workingList.find(
      (it) =>
        it.status !== 'completed' &&
        isDuplicateCandidate(it, title, startIso, hasExplicitStart, tzCtx.offsetMinutes)
    )
    if (matchedExisting) {
      repeated.push(enrichScheduleItem({ ...matchedExisting, matched_input_title: title }))
      continue
    }

    const deadlineIso = normalizeIso(raw.deadline, tzCtx.offsetStr)
    const movability = raw.movability || 'fluid'
    const flexibility = movability !== 'immovable'
    const priority = Math.max(1, Math.min(4, Number(raw.priority) || 3))
    const taskType = raw.task_type || raw.category || 'task'

    const noteParts = []
    if (raw.venue) noteParts.push(`Venue: ${raw.venue}`)
    if (raw.transition_reason) noteParts.push(raw.transition_reason)

    const rowToInsert = {
      user_id: userId,
      title,
      task_type: taskType,
      start_time: startIso,
      end_time: endIso,
      deadline: deadlineIso,
      priority,
      flexibility,
      status: 'scheduled',
      completion_note: noteParts.length > 0 ? noteParts.join(' · ') : null,
    }

    if (supabase) {
      const { data, error } = await supabase
        .from('schedule_items')
        .insert(rowToInsert)
        .select()
        .single()

      if (error) {
        console.warn('Supabase insert schedule_items error:', error.message)
        const fallback = enrichScheduleItem({
          id: `temp-${Date.now()}-${saved.length}`,
          ...rowToInsert,
          movability,
          transition_reason: raw.transition_reason || null,
        })
        saved.push(fallback)
        workingList.push(fallback)
      } else if (data) {
        const enriched = enrichScheduleItem({
          ...data,
          movability,
          transition_reason: raw.transition_reason || null,
        })
        saved.push(enriched)
        workingList.push(enriched)
      }
    } else {
      const fallback = enrichScheduleItem({
        id: `local-${Date.now()}-${saved.length}`,
        ...rowToInsert,
        movability,
        transition_reason: raw.transition_reason || null,
      })
      saved.push(fallback)
      workingList.push(fallback)
    }
  }

  return { saved, repeated }
}

