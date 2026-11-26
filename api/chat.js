import {
  setCors,
  getUserId,
  getTimezoneContext,
  getSupabase,
  callGemini,
  buildExtractionPrompt,
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

  try {
    const prompt = buildExtractionPrompt(tzCtx, message)
    const parsed = await callGemini({
      parts: [{ text: prompt }],
      jsonMode: true,
      perModelTimeoutMs: 15000,
    })

    const extractedTasks = Array.isArray(parsed?.tasks) ? parsed.tasks : []
    const { saved: savedTasks, repeated: repeatedTasks } = await saveExtractedTasks(
      supabase,
      userId,
      extractedTasks,
      tzCtx
    )

    let replyMessage = parsed?.message || ''
    let action = 'chat'

    if (savedTasks.length === 0 && repeatedTasks.length > 0) {
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
        replyMessage || 'Got it! Let me know if you want me to add any tasks or times to your schedule.'
    }

    return res.status(200).json({
      message: replyMessage,
      action,
      tasks_saved: savedTasks,
      tasks_extracted: savedTasks,
      repeated_tasks: repeatedTasks,
      clarification_needed: parsed?.clarification_needed || null,
      schedule_trigger: savedTasks.length > 0,
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
