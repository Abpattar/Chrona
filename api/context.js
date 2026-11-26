import {
  setCors,
  getUserId,
  getTimezoneContext,
  getSupabase,
  enrichScheduleItem,
  deduplicateScheduleItems,
} from './_lib/chrona.js'

export default async function handler(req, res) {
  setCors(res, 'GET, OPTIONS')

  if (req.method === 'OPTIONS') {
    return res.status(200).end()
  }

  const userId = getUserId(req)
  const tzCtx = getTimezoneContext(req)
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

  items = deduplicateScheduleItems(items, tzCtx.offsetMinutes)

  const todayStr = tzCtx.localDateStr
  const today = items.filter((t) => {
    if (!t.start_time) return false
    const ms = new Date(t.start_time).getTime()
    if (Number.isNaN(ms)) return t.start_time.startsWith(todayStr)
    const localDay = new Date(ms + tzCtx.offsetMinutes * 60000).toISOString().slice(0, 10)
    return localDay === todayStr
  })

  return res.status(200).json({
    success: true,
    user_id: userId,
    today,
    this_week: items,
    active_focus: (today.find((t) => t.status !== 'completed') || items.find((t) => t.status !== 'completed')) || null,
    stats: {
      total: items.length,
      completed: items.filter((t) => t.status === 'completed').length,
    },
  })
}
