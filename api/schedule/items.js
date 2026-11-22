import { createClient } from '@supabase/supabase-js'

const SUPABASE_URL = process.env.SUPABASE_URL
const SUPABASE_ANON_KEY = process.env.SUPABASE_ANON_KEY

let supabase = null
if (SUPABASE_URL && SUPABASE_ANON_KEY) {
  try {
    supabase = createClient(SUPABASE_URL, SUPABASE_ANON_KEY)
  } catch (e) {
    console.warn('Could not initialize Supabase:', e.message)
  }
}

export default async function handler(req, res) {
  res.setHeader('Access-Control-Allow-Origin', '*')
  res.setHeader('Access-Control-Allow-Methods', 'GET, OPTIONS')
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, X-User-ID, X-Timezone, X-Timezone-Offset')

  if (req.method === 'OPTIONS') {
    return res.status(200).end()
  }

  const userId = req.headers['x-user-id'] || '00000000-0000-0000-0000-000000000001'
  let items = []

  if (supabase) {
    try {
      const { data } = await supabase
        .from('schedule_items')
        .select('*')
        .eq('user_id', userId)
        .order('start_time', { ascending: true })

      if (data && data.length > 0) {
        items = data
      } else {
        const { data: evts } = await supabase
          .from('events')
          .select('*')
          .eq('user_id', userId)
          .order('event_datetime', { ascending: true })
        if (evts) {
          items = evts.map(e => ({
            id: e.id,
            title: e.title,
            start_time: e.event_datetime || e.fixed_start,
            end_time: e.fixed_end,
            duration_minutes: e.duration_minutes || 60,
            priority: e.priority || 2,
            movability: e.flexibility === false ? 'immovable' : 'negotiable',
            status: e.status || 'scheduled',
          }))
        }
      }
    } catch (err) {
      console.warn('Supabase fetch error in /api/schedule/items:', err.message)
    }
  }

  return res.status(200).json({
    success: true,
    items,
  })
}

