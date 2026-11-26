import { setCors, getUserId, getSupabase } from './_lib/chrona.js'

export default async function handler(req, res) {
  setCors(res, 'GET, OPTIONS')

  if (req.method === 'OPTIONS') return res.status(200).end()

  const userId = getUserId(req)
  const supabase = getSupabase()
  let events = []

  if (supabase) {
    try {
      const { data } = await supabase
        .from('events')
        .select('*')
        .eq('user_id', userId)
        .order('event_datetime', { ascending: true })
      if (Array.isArray(data)) events = data
    } catch (err) {
      console.warn('Supabase fetch error in /api/events:', err.message)
    }
  }

  return res.status(200).json({ success: true, events })
}

