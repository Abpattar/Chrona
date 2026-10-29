import { createClient } from '@supabase/supabase-js'

const SUPABASE_URL = process.env.SUPABASE_URL
const SUPABASE_ANON_KEY = process.env.SUPABASE_ANON_KEY

if (!SUPABASE_URL || !SUPABASE_ANON_KEY) {
    console.warn('Missing Supabase environment variables.')
}

const supabase = createClient(SUPABASE_URL, SUPABASE_ANON_KEY)

export default async function handler(req, res) {
    res.setHeader('Access-Control-Allow-Origin', '*')
    res.setHeader('Access-Control-Allow-Methods', 'GET, OPTIONS')
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type, X-User-ID')

    if (req.method === 'OPTIONS') {
        return res.status(200).end()
    }

    if (req.method !== 'GET') {
        return res.status(405).json({ error: 'Method not allowed' })
    }

    try {
        const userId = req.headers['x-user-id'] || '00000000-0000-0000-0000-000000000001'

        const { data, error } = await supabase
            .from('events')
            .select('*')
            .eq('user_id', userId)
            .order('event_datetime', { ascending: true })

        if (error) {
            throw new Error(`Supabase error: ${error.message}`)
        }

        return res.status(200).json({
            success: true,
            items: data || [],
        })
    } catch (err) {
        console.error('Schedule items error:', err)
        return res.status(500).json({
            error: err.message || 'Internal server error',
        })
    }
}