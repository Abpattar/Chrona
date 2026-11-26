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

export async function fetchActiveScheduleItems(supabase, userId, offsetMinutes = 330) {
  if (!supabase) return []
  try {
    const { data, error } = await supabase
      .from('schedule_items')
      .select('*')
      .eq('user_id', userId)
      .neq('status', 'cancelled')
      .order('start_time', { ascending: true })
    if (error || !Array.isArray(data)) return []
    return deduplicateScheduleItems(data.map(enrichScheduleItem), offsetMinutes)
  } catch (err) {
    console.warn('fetchActiveScheduleItems error:', err.message)
    return []
  }
}

function formatItemForPrompt(item, tzCtx) {
  let when = 'Unscheduled / Flexible'
  if (item.start_time) {
    const sMs = new Date(item.start_time).getTime()
    const eMs = item.end_time ? new Date(item.end_time).getTime() : NaN
    if (!Number.isNaN(sMs)) {
      const sLoc = new Date(sMs + tzCtx.offsetMinutes * 60000).toISOString().slice(0, 16).replace('T', ' ')
      const dayIdx = new Date(sMs + tzCtx.offsetMinutes * 60000).getUTCDay()
      const dayName = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'][dayIdx]
      const eStr = !Number.isNaN(eMs)
        ? new Date(eMs + tzCtx.offsetMinutes * 60000).toISOString().slice(11, 16)
        : ''
      when = `${sLoc}${eStr ? `-${eStr}` : ''} (${dayName})`
    }
  }
  return `- id="${item.id}" | title="${item.title}" | type=${item.task_type || 'task'} | status=${item.status || 'scheduled'} | when=${when} | duration=${item.duration_minutes || 60}m | movability=${item.movability || 'fluid'}`
}

export function buildChatAgentPrompt(tzCtx, existingItems = [], userMessage = '', history = []) {
  const scheduleLines =
    existingItems.length > 0
      ? existingItems.map((it) => formatItemForPrompt(it, tzCtx)).join('\n')
      : '(Schedule is currently empty)'

  const historyLines =
    Array.isArray(history) && history.length > 0
      ? history
          .slice(-6)
          .map((h) => `${h.role === 'user' ? 'User' : 'Chrona'}: ${String(h.content || '').slice(0, 300)}`)
          .join('\n')
      : ''

  return `You are Chrona, an intelligent AI time-management copilot with FULL control over the user's schedule.

Current user local date & time: ${tzCtx.nowDisplay} (Timezone: ${tzCtx.tzName}, offset ${tzCtx.offsetStr})

CURRENT SCHEDULE ITEMS IN DATABASE:
${scheduleLines}

${historyLines ? `RECENT CONVERSATION:\n${historyLines}\n` : ''}USER MESSAGE:
${userMessage}

You can perform ANY schedule management request:
1. ADD new task(s)/event(s) -> put them in "tasks".
2. CANCEL + RESCHEDULE / MOVE / MODIFY an existing item (e.g. "My exam got cancelled on 28 Nov, now it is on 1 Dec", "Move my meeting to 6pm", "Rename X to Y") -> put a "reschedule" or "update" operation in "operations" targeting that item's "target_id" (and KEEP its original title like "Maths Exam" and original time-of-day/duration unless the user changed them). DO NOT also put it in "tasks"!
3. CANCEL / DELETE / REMOVE specific item(s) (e.g. "Cancel my exam on Nov 28", "Remove all meetings tomorrow") -> put "cancel" operations in "operations" (use "target_id" for a single item, or "match_title" / "match_date" for matching items).
4. CLEAR A DATE / WEEK / MONTH / ENTIRE SCHEDULE (e.g. "Clear all schedule this month", "Delete everything in December", "Clear today's schedule", "Clear all tasks") -> put "clear_range" (with "range_start" and "range_end" ISO datetimes with offset ${tzCtx.offsetStr}) or "clear_all" in "operations".
5. MARK COMPLETE -> put "complete" operation in "operations".
6. ANSWER QUESTIONS (e.g. "What do I have this week?", "Am I free Friday?") -> leave "operations": [] and "tasks": [] and answer accurately from CURRENT SCHEDULE ITEMS above.

Return ONLY valid JSON with this exact structure:
{
  "message": "Clear, concise reply confirming exact actions taken or answering the user's question.",
  "operations": [
    {
      "op": "reschedule|update|cancel|delete|complete|clear_range|clear_all",
      "target_id": "exact UUID from CURRENT SCHEDULE ITEMS if targeting a specific item, else null",
      "match_title": "optional title/keyword to match when target_id is null (e.g. 'exam' or 'meeting')",
      "match_date": "optional YYYY-MM-DD local date to match old item(s) (e.g. '2026-11-28')",
      "all_matching": false,
      "range_start": "ISO datetime with offset ${tzCtx.offsetStr} (for clear_range only, else null)",
      "range_end": "ISO datetime with offset ${tzCtx.offsetStr} (for clear_range only, else null)",
      "updates": {
        "title": "optional new/preserved title",
        "start_time": "optional new ISO datetime with offset ${tzCtx.offsetStr}",
        "end_time": "optional new ISO datetime with offset ${tzCtx.offsetStr}",
        "duration_minutes": 60,
        "priority": 2,
        "movability": "immovable|negotiable|fluid",
        "completion_note": "optional note (e.g. 'Rescheduled from Nov 28')"
      }
    }
  ],
  "tasks": [
    {
      "title": "ONLY brand-new tasks to add (never duplicate items already moved/updated in operations!)",
      "category": "exam|quiz|assignment|class|meeting|study|workout|family|social|appointment|personal|reminder|project|other",
      "task_type": "exam|quiz|assignment|class|meeting|study|workout|family|social|appointment|task",
      "venue": "string or null",
      "start_time": "ISO 8601 datetime with offset ${tzCtx.offsetStr} or null if flexible",
      "end_time": "ISO 8601 datetime with offset ${tzCtx.offsetStr} or null",
      "deadline": "ISO 8601 datetime with offset ${tzCtx.offsetStr} or null",
      "duration_minutes": 60,
      "priority": 2,
      "movability": "immovable|negotiable|fluid",
      "transition_reason": "short buffer note or null"
    }
  ],
  "clarification_needed": null
}

Critical Rules:
- If user says "X on <old date> was cancelled/moved and is now on <new date>", ALWAYS emit ONE "reschedule" operation on X's target_id moving it to <new date> (preserving X's original title, duration, and time-of-day if user didn't specify a new time), OR if it's a different new event, emit "cancel" on the old item AND put the new item in "tasks". NEVER leave the cancelled old event behind!
- Always use timezone offset ${tzCtx.offsetStr} on all ISO datetimes.
- For "this month" when today is ${tzCtx.localDateStr}, range_start is "${tzCtx.localDateStr.slice(0, 7)}-01T00:00:00${tzCtx.offsetStr}" and range_end is the last day of that month at "23:59:59${tzCtx.offsetStr}".`
}

function itemLocalDate(item, offsetMinutes) {
  if (!item?.start_time) return null
  const ms = new Date(item.start_time).getTime()
  if (Number.isNaN(ms)) return null
  return new Date(ms + offsetMinutes * 60000).toISOString().slice(0, 10)
}

function findOperationTargets(op, workingItems, offsetMinutes) {
  if (op.target_id) {
    const exact = workingItems.find((it) => it.id === op.target_id)
    if (exact) return [exact]
  }

  const matchDate = (op.match_date || '').trim()
  const matchTitle = (op.match_title || '').trim().toLowerCase()

  let candidates = workingItems.filter((it) => it.status !== 'cancelled')
  if (matchDate) {
    candidates = candidates.filter((it) => itemLocalDate(it, offsetMinutes) === matchDate)
  }
  if (matchTitle) {
    const tokB = coreTitleTokens(matchTitle)
    const filtered = candidates.filter((it) => {
      const tLow = (it.title || '').toLowerCase()
      const typeLow = (it.task_type || '').toLowerCase()
      if (tLow.includes(matchTitle) || matchTitle.includes(tLow) || typeLow === matchTitle) {
        return true
      }
      const tokA = coreTitleTokens(it.title)
      return tokB.some((t) => tokA.includes(t))
    })
    candidates = filtered
  }

  if (op.all_matching) return candidates
  return candidates.length > 0 ? [candidates[0]] : []
}

export async function executeScheduleOperations(supabase, userId, operations, existingItems, tzCtx) {
  const updatedTasks = []
  const cancelledTasks = []
  const completedTasks = []
  let workingItems = [...(existingItems || [])]

  if (!Array.isArray(operations) || operations.length === 0) {
    return { updatedTasks, cancelledTasks, completedTasks, workingItems }
  }

  for (const op of operations) {
    const kind = String(op?.op || '').toLowerCase()
    if (!kind) continue

    if (kind === 'clear_all') {
      const targets = workingItems.filter((it) => it.status !== 'cancelled')
      if (supabase) {
        await supabase.from('schedule_items').delete().eq('user_id', userId)
      }
      cancelledTasks.push(...targets)
      workingItems = []
      continue
    }

    if (kind === 'clear_range') {
      const rStartIso = normalizeIso(op.range_start, tzCtx.offsetStr)
      const rEndIso = normalizeIso(op.range_end, tzCtx.offsetStr)
      if (!rStartIso || !rEndIso) continue
      const rStartMs = new Date(rStartIso).getTime()
      const rEndMs = new Date(rEndIso).getTime()

      const targets = workingItems.filter((it) => {
        const refTime = it.start_time || it.deadline
        if (!refTime) return false
        const ms = new Date(refTime).getTime()
        return !Number.isNaN(ms) && ms >= rStartMs && ms <= rEndMs
      })

      if (targets.length > 0 && supabase) {
        const ids = targets.map((t) => t.id).filter(Boolean)
        if (ids.length > 0) {
          await supabase
            .from('schedule_items')
            .delete()
            .eq('user_id', userId)
            .in('id', ids)
        }
      }

      const removedIds = new Set(targets.map((t) => t.id))
      cancelledTasks.push(...targets)
      workingItems = workingItems.filter((it) => !removedIds.has(it.id))
      continue
    }

    const targets = findOperationTargets(op, workingItems, tzCtx.offsetMinutes)
    if (targets.length === 0) continue

    if (kind === 'cancel' || kind === 'delete') {
      const ids = targets.map((t) => t.id).filter(Boolean)
      if (supabase && ids.length > 0) {
        await supabase
          .from('schedule_items')
          .delete()
          .eq('user_id', userId)
          .in('id', ids)
      }
      const removedIds = new Set(ids)
      cancelledTasks.push(...targets)
      workingItems = workingItems.filter((it) => !removedIds.has(it.id))
      continue
    }

    if (kind === 'complete') {
      const ids = targets.map((t) => t.id).filter(Boolean)
      const nowIso = new Date().toISOString()
      if (supabase && ids.length > 0) {
        await supabase
          .from('schedule_items')
          .update({ status: 'completed', actual_end: nowIso })
          .eq('user_id', userId)
          .in('id', ids)
      }
      for (const t of targets) {
        const doneItem = enrichScheduleItem({ ...t, status: 'completed', actual_end: nowIso })
        completedTasks.push(doneItem)
        workingItems = workingItems.map((it) => (it.id === t.id ? doneItem : it))
      }
      continue
    }

    if (kind === 'reschedule' || kind === 'update' || kind === 'move') {
      const u = op.updates || {}
      for (const target of targets) {
        const durMin = Math.max(
          15,
          Math.min(720, Number(u.duration_minutes || target.duration_minutes) || 60)
        )
        let newStartIso = normalizeIso(u.start_time, tzCtx.offsetStr)
        let newEndIso = normalizeIso(u.end_time, tzCtx.offsetStr)

        if (!newStartIso && kind === 'reschedule') {
          const others = workingItems.filter((it) => it.id !== target.id)
          const oldMs = target.start_time ? new Date(target.start_time).getTime() : null
          const slot = findNextOpenSlot(durMin, others, tzCtx, oldMs)
          newStartIso = slot.startIso
          newEndIso = slot.endIso
        }

        if (newStartIso && (!newEndIso || new Date(newEndIso).getTime() <= new Date(newStartIso).getTime())) {
          newEndIso = new Date(new Date(newStartIso).getTime() + durMin * 60000).toISOString()
        }

        const patch = { status: u.status || 'scheduled' }
        if (u.title && u.title.trim()) patch.title = u.title.trim()
        if (newStartIso) patch.start_time = newStartIso
        if (newEndIso) patch.end_time = newEndIso
        if (u.priority !== undefined) patch.priority = Math.max(1, Math.min(4, Number(u.priority) || 3))
        if (u.movability) patch.flexibility = u.movability !== 'immovable'
        if (u.completion_note !== undefined) patch.completion_note = u.completion_note

        let updatedRow = { ...target, ...patch }
        if (supabase && target.id) {
          const { data, error } = await supabase
            .from('schedule_items')
            .update(patch)
            .eq('id', target.id)
            .eq('user_id', userId)
            .select()
            .single()
          if (!error && data) {
            updatedRow = data
          } else if (error) {
            console.warn('Update schedule_items error:', error.message)
          }
        }

        const enriched = enrichScheduleItem({
          ...updatedRow,
          movability: u.movability || target.movability,
          auto_rescheduled: kind === 'reschedule',
        })
        updatedTasks.push(enriched)
        workingItems = workingItems.map((it) => (it.id === target.id ? enriched : it))
      }
    }
  }

  return { updatedTasks, cancelledTasks, completedTasks, workingItems }
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

export function findNextOpenSlot(durationMin, existingItems, tzCtx, earliestStartMs = null) {
  const durMs = Math.max(15, durationMin || 60) * 60 * 1000
  const busy = existingItems
    .filter((it) => it.start_time && it.end_time && it.status !== 'cancelled' && it.status !== 'completed')
    .map((it) => ({
      start: new Date(it.start_time).getTime(),
      end: new Date(it.end_time).getTime(),
    }))
    .filter((b) => !Number.isNaN(b.start) && !Number.isNaN(b.end))

  const baseMs = Math.max(
    tzCtx.now.getTime() + 15 * 60000,
    earliestStartMs ? earliestStartMs + 30 * 60000 : 0
  )
  // Start checking from the next 30-minute boundary
  let cursor = new Date(Math.ceil(baseMs / (30 * 60000)) * (30 * 60000))

  for (let i = 0; i < 672; i++) {
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

