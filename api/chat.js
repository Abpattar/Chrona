import { GoogleGenerativeAI } from '@google/generative-ai'

const GEMINI_API_KEY = process.env.GEMINI_API_KEY
let genAI = null
if (GEMINI_API_KEY) {
  try {
    genAI = new GoogleGenerativeAI(GEMINI_API_KEY)
  } catch (e) {
    console.warn('Could not init Gemini in api/chat:', e.message)
  }
}

export default async function handler(req, res) {
  res.setHeader('Access-Control-Allow-Origin', '*')
  res.setHeader('Access-Control-Allow-Methods', 'POST, OPTIONS')
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, X-User-ID, X-Timezone, X-Timezone-Offset')

  if (req.method === 'OPTIONS') {
    return res.status(200).end()
  }

  if (req.method !== 'POST') {
    return res.status(405).json({ error: 'Method not allowed' })
  }

  const { message } = req.body || {}
  if (!message) {
    return res.status(400).json({ error: 'Message is required' })
  }

  if (genAI) {
    try {
      const model = genAI.getGenerativeModel({ model: 'gemini-1.5-flash-latest' })
      const prompt = `You are Chrona, an AI time-management copilot. Respond concisely and professionally to the user's scheduling query:\n\n${message}`
      const result = await model.generateContent(prompt)
      const text = result.response.text()
      return res.status(200).json({
        message: text,
        tasks_saved: [],
        tasks_extracted: [],
      })
    } catch (err) {
      console.warn('Gemini chat error in api/chat:', err.message)
    }
  }

  return res.status(200).json({
    message: `I received your message: "${message}". Connect your local backend with 'python3 run_servers.py' for real-time dynamic transition calculation and timetable vision parsing.`,
    tasks_saved: [],
    tasks_extracted: [],
  })
}

