import { setCors, getUserId, getSupabase } from '../_lib/chrona.js'

export default async function handler(req, res) {
  setCors(res, 'POST, DELETE, OPTIONS')

  if (req.method === 'OPTIONS') return res.status(200).end()
  if (!['POST', 'DELETE'].includes(req.method)) {
    return res.status(405).json({ error: 'Method not allowed' })
  }

  const userId = getUserId(req)
  const supabase = getSupabase()
  if (supabase) {
    const { error } = await supabase.from('schedule_items').delete().eq('user_id', userId)
    if (error) {
      const { error: updErr } = await supabase
        .from('schedule_items')
        .update({ status: 'cancelled' })
        .eq('user_id', userId)
      if (updErr) {
        return res.status(500).json({ error: updErr.message, detail: updErr.message })
      }
    }
  }

  return res.status(200).json({ success: true, message: 'Schedule cleared' })
}
