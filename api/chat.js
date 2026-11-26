import {
  setCors,
  getUserId,
  getTimezoneContext,
  getSupabase,
  callGemini,
  fetchRawActiveScheduleItems,
  deduplicateScheduleItems,
  buildChatAgentPrompt,
  executeScheduleOperations,
  saveExtractedTasks,
} from './_lib/chrona.js'

export default async function handler(req, res) {
  setCors(res, 'POST, OPTIONS')

  if (req.method === 'OPTIONS') {
    return res.status(200).end()
  }

  if (req.method !== 'POST') {
    return res.status(405).json({ error: 'Method not allowed', detail: 'HTTP 405: Use POST' })
  }

  let body = req.body
  if (typeof body === 'string') {
    try {
      body = JSON.parse(body)
    } catch {
      body = {}
    }
  }
  body = body || {}

  const message = (body.message || '').trim()
  if (!message) {
    return res.status(400).json({ error: 'Message is required', detail: 'Message is required' })
  }

  const userId = getUserId(req)
  const tzCtx = getTimezoneContext(req, body)
  const supabase = getSupabase()
  const history = Array.isArray(body.history)
    ? body.history
    : Array.isArray(body.context?.history)
    ? body.context.history
    : []

  try {
    // 1. Load ALL raw active items (for thorough deletion/updates) + deduplicated view for prompt
    const rawItems = await fetchRawActiveScheduleItems(supabase, userId)
    const promptItems = deduplicateScheduleItems(rawItems, tzCtx.offsetMinutes)

    // 2. Ask Gemini with full schedule + conversation context
    const prompt = buildChatAgentPrompt(tzCtx, promptItems, message, history)
    const parsed = await callGemini({
      parts: [{ text: prompt }],
      jsonMode: true,
      perModelTimeoutMs: 18000,
    })

    // 3. Execute schedule operations against ALL raw rows (so duplicate DB copies are never orphaned)
    const operations = Array.isArray(parsed?.operations) ? parsed.operations : []
    const {
      updatedTasks,
      cancelledTasks,
      completedTasks,
    } = await executeScheduleOperations(supabase, userId, operations, rawItems, tzCtx)

    // 4. Filter out any task in parsed.tasks that was already handled as an in-place update/completion
    const handledTitles = new Set(
      [...updatedTasks, ...completedTasks].map((t) => (t.title || '').trim().toLowerCase())
    )
    const rawNewTasks = (Array.isArray(parsed?.tasks) ? parsed.tasks : []).filter(
      (t) => !handledTitles.has((t?.title || '').trim().toLowerCase())
    )

    // 5. Add brand-new tasks (with repeated-task detection against the updated schedule)
    const { saved: savedTasks, repeated: repeatedTasks } = await saveExtractedTasks(
      supabase,
      userId,
      rawNewTasks,
      tzCtx
    )

    const allDisplayTasks = [...updatedTasks, ...completedTasks, ...savedTasks]
    const displayCancelled = deduplicateScheduleItems(cancelledTasks, tzCtx.offsetMinutes)

    let replyMessage = parsed?.message || ''
    let action = 'chat'

    if (displayCancelled.length > 0 && updatedTasks.length === 0 && savedTasks.length === 0) {
      action = 'schedule_cleared'
      if (!replyMessage) {
        replyMessage = `Removed ${displayCancelled.length} item(s) from your schedule.`
      }
    } else if (updatedTasks.length > 0 || completedTasks.length > 0) {
      action = 'schedule_updated'
      if (!replyMessage) {
        replyMessage = `Updated ${updatedTasks.length + completedTasks.length} item(s) on your schedule.`
      }
    } else if (savedTasks.length === 0 && repeatedTasks.length > 0) {
      const list = repeatedTasks.map((t) => `• ${t.title}`).join('\n')
      replyMessage = `🔁 Repeated task${repeatedTasks.length > 1 ? 's' : ''} — already on your schedule:\n${list}\n\nNo duplicate was added. Ask me if you want to reschedule or update ${repeatedTasks.length > 1 ? 'them' : 'it'}.`
      action = 'repeated_task'
    } else if (savedTasks.length > 0 && repeatedTasks.length > 0) {
      const names = repeatedTasks.map((t) => `"${t.title}"`).join(', ')
      replyMessage = `${replyMessage || `Scheduled ${savedTasks.length} new item(s).`}\n\n🔁 Skipped ${repeatedTasks.length} repeated task(s) already scheduled: ${names}.`
      action = 'schedule_saved'
    } else if (savedTasks.length > 0) {
      replyMessage = replyMessage || `Scheduled ${savedTasks.length} item(s) for you.`
      action = 'schedule_saved'
    } else {
      replyMessage =
        replyMessage || 'Got it! Let me know if you want me to add, update, or clear anything on your schedule.'
    }

    return res.status(200).json({
      message: replyMessage,
      action,
      tasks_saved: allDisplayTasks,
      tasks_extracted: allDisplayTasks,
      tasks_updated: updatedTasks,
      tasks_cancelled: displayCancelled,
      tasks_completed: completedTasks,
      repeated_tasks: repeatedTasks,
      clarification_needed: parsed?.clarification_needed || null,
      schedule_trigger:
        allDisplayTasks.length > 0 || displayCancelled.length > 0,
      quick_actions: [],
    })
  } catch (err) {
    console.error('Gemini chat error in api/chat:', err.message)
    return res.status(500).json({
      error: err.message || 'AI chat failed',
      detail: err.message || 'AI chat failed',
    })
  }
}
