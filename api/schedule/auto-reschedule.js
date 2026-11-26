import {
  setCors,
  getUserId,
  getTimezoneContext,
  getSupabase,
  enrichScheduleItem,
  findNextOpenSlot,
} from '../_lib/chrona.js'

function formatLocalSlot(isoStr, tzCtx) {
  try {
    const dt = new Date(isoStr)
    return new Intl.DateTimeFormat('en-US', {
      timeZone: tzCtx.tzName,
      weekday: 'short',
      month: 'short',
      day: 'numeric',
      hour: 'numeric',
      minute: '2-digit',
    }).format(dt)
  } catch {
    return isoStr
  }
}

export default async function handler(req, res) {
  setCors(res, 'POST, OPTIONS')

  if (req.method === 'OPTIONS') return res.status(200).end()
  if (req.method !== 'POST') {
    return res.status(405).json({ error: 'Method not allowed', detail: 'HTTP 405: Use POST' })
  }

  const body = typeof req.body === 'string' ? JSON.parse(req.body || '{}') : (req.body || {})
  const taskId = body.task_id || null
  const allUnplaced = Boolean(body.all_unplaced)
  const userId = getUserId(req)
  const tzCtx = getTimezoneContext(req, body)
  const supabase = getSupabase()

  if (!supabase) {
    return res.status(500).json({ error: 'Database not configured', detail: 'Database not configured' })
  }

  try {
    const { data: allItems, error: fetchErr } = await supabase
      .from('schedule_items')
      .select('*')
      .eq('user_id', userId)
      .neq('status', 'cancelled')

    if (fetchErr) throw new Error(fetchErr.message)
    const items = (allItems || []).map(enrichScheduleItem)

    let targets = []
    if (taskId) {
      const found = items.find((it) => it.id === taskId)
      if (!found) {
        return res.status(404).json({ error: 'Task not found', detail: 'Task not found' })
      }
      targets = [found]
    } else if (allUnplaced) {
      targets = items.filter(
        (it) => it.status === 'pending' || it.status === 'skipped' || !it.start_time
      )
    }

    if (targets.length === 0) {
      return res.status(200).json({
        success: true,
        message: 'All tasks are already placed cleanly.',
        rescheduled_items: [],
      })
    }

    const rescheduled = []
    let workingList = [...items]

    for (const task of targets) {
      const others = workingList.filter((it) => it.id !== task.id)
      const currentStartMs = task.start_time ? new Date(task.start_time).getTime() : null
      const durMin = task.duration_minutes || 60
      const { startIso, endIso } = findNextOpenSlot(durMin, others, tzCtx, currentStartMs)

      const newNote = `Auto-rescheduled to ${formatLocalSlot(startIso, tzCtx)}`
      const { data: updated, error: updErr } = await supabase
        .from('schedule_items')
        .update({
          start_time: startIso,
          end_time: endIso,
          status: 'scheduled',
          completion_note: newNote,
        })
        .eq('id', task.id)
        .eq('user_id', userId)
        .select()
        .single()

      if (updErr) throw new Error(updErr.message)

      const enriched = enrichScheduleItem(updated)
      workingList = [...others, enriched]
      rescheduled.push({
        ...enriched,
        task_id: enriched.id,
        rescheduled_to: startIso,
        auto_rescheduled: true,
      })
    }

    const msg =
      rescheduled.length === 1
        ? `Rescheduled "${rescheduled[0].title}" to ${formatLocalSlot(rescheduled[0].start_time, tzCtx)} ⚡`
        : `Rescheduled ${rescheduled.length} task(s) into open focus slots ⚡`

    return res.status(200).json({
      success: true,
      message: msg,
      rescheduled_items: rescheduled,
    })
  } catch (err) {
    console.error('auto-reschedule error:', err)
    return res.status(500).json({
      error: err.message || 'Failed to reschedule',
      detail: err.message || 'Failed to reschedule',
    })
  }
}
