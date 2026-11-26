import {
  setCors,
  getUserId,
  getSupabase,
  enrichScheduleItem,
} from './_lib/chrona.js'

export default async function handler(req, res) {
  setCors(res, 'GET, OPTIONS')

  if (req.method === 'OPTIONS') {
    return res.status(200).end()
  }

  const userId = getUserId(req)
  const supabase = getSupabase()
  let items = []

  if (supabase) {
    try {
      const { data } = await supabase
        .from('schedule_items')
        .select('*')
        .eq('user_id', userId)
        .neq('status', 'cancelled')
        .order('start_time', { ascending: true })

      if (data && data.length > 0) {
        items = data.map(enrichScheduleItem)
      } else {
        const { data: evts } = await supabase
          .from('events')
          .select('*')
          .eq('user_id', userId)
          .order('event_datetime', { ascending: true })
        if (evts) {
          items = evts.map((e) =>
            enrichScheduleItem({
              id: e.id,
              title: e.title,
              start_time: e.event_datetime || e.fixed_start,
              end_time: e.fixed_end,
              duration_minutes: e.duration_minutes || 60,
              priority: e.priority || 2,
              movability: e.flexibility === false ? 'immovable' : 'negotiable',
              status: e.status || 'scheduled',
            })
          )
        }
      }
    } catch (err) {
      console.warn('Supabase fetch error in /api/context:', err.message)
    }
  }

  const todayStr = new Date().toISOString().split('T')[0]
  const today = items.filter((t) => t.start_time && t.start_time.startsWith(todayStr))

  return res.status(200).json({
    success: true,
    user_id: userId,
    today: today.length > 0 ? today : items.slice(0, 5),
    this_week: items,
    active_focus: items.find((t) => t.status !== 'completed') || null,
    stats: {
      total: items.length,
      completed: items.filter((t) => t.status === 'completed').length,
    },
  })
}
