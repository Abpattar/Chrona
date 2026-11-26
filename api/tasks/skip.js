import { setCors, getUserId, getSupabase } from '../_lib/chrona.js'

export default async function handler(req, res) {
  setCors(res, 'POST, OPTIONS')

  if (req.method === 'OPTIONS') return res.status(200).end()
  if (req.method !== 'POST') return res.status(405).json({ error: 'Method not allowed' })

  const body = typeof req.body === 'string' ? JSON.parse(req.body || '{}') : (req.body || {})
  const taskId = body.task_id
  if (!taskId) return res.status(400).json({ error: 'task_id is required' })

  const userId = getUserId(req)
  const supabase = getSupabase()
  if (supabase) {
    await supabase
      .from('schedule_items')
      .update({
        status: 'skipped',
        completion_note: body.note || 'Deferred by user',
      })
      .eq('id', taskId)
      .eq('user_id', userId)
  }

  return res.status(200).json({ success: true, task_id: taskId, status: 'skipped' })
}

