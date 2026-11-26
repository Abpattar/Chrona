import { setCors, getUserId, getSupabase } from '../../_lib/chrona.js'

export default async function handler(req, res) {
  setCors(res, 'PATCH, DELETE, OPTIONS')

  if (req.method === 'OPTIONS') return res.status(200).end()
  if (!['PATCH', 'DELETE'].includes(req.method)) {
    return res.status(405).json({ error: 'Method not allowed' })
  }

  const { id } = req.query || {}
  if (!id) return res.status(400).json({ error: 'Item id is required' })

  const userId = getUserId(req)
  const supabase = getSupabase()
  if (!supabase) return res.status(200).json({ success: true, id })

  if (req.method === 'DELETE') {
    await supabase.from('schedule_items').delete().eq('id', id).eq('user_id', userId)
    return res.status(200).json({ success: true, id })
  }

  const body = typeof req.body === 'string' ? JSON.parse(req.body || '{}') : (req.body || {})
  const allowed = {}
  for (const key of ['title', 'status', 'start_time', 'end_time', 'priority', 'flexibility', 'completion_note']) {
    if (body[key] !== undefined) allowed[key] = body[key]
  }

  const { data, error } = await supabase
    .from('schedule_items')
    .update(allowed)
    .eq('id', id)
    .eq('user_id', userId)
    .select()
    .single()

  if (error) {
    return res.status(500).json({ error: error.message })
  }

  return res.status(200).json({ success: true, item: data })
}

