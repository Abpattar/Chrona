import { IncomingForm } from 'formidable'
import fs from 'fs'
import { callGemini, getSupabase, getUserId, setCors } from './_lib/chrona.js'

function getExtractionPrompt() {
  const today = new Date().toISOString().split('T')[0]
  return `You are an event extraction engine.

Analyze the input and extract event information.

Return JSON in this exact format:
{
  "title": "",
  "category": "",
  "venue": "",
  "event_datetime": ""
}

Rules:
category must be one of: exam, hackathon, assignment, meeting, personal, reminder
Convert relative times using today's date (${today}):
  "today" -> today's date
  "tomorrow" -> tomorrow's date
Convert datetime to ISO format: YYYY-MM-DDTHH:MM:SS
Return ONLY valid JSON. If a field is missing, return null for that field.`
}

async function insertIntoSupabase(eventData, userId) {
  const cleanData = { user_id: userId }
  for (const key of ['title', 'category', 'venue', 'event_datetime']) {
    if (eventData[key] != null) {
      cleanData[key] = eventData[key]
    }
  }

  if (!cleanData.title) {
    throw new Error('Extracted event has no title')
  }

  const supabase = getSupabase()
  if (!supabase) return true

  const { error } = await supabase.from('events').insert(cleanData)
  if (error) {
    throw new Error(`Supabase error: ${error.message}`)
  }
  return true
}

function parseForm(req) {
  return new Promise((resolve, reject) => {
    const form = new IncomingForm({
      keepExtensions: true,
      maxFileSize: 10 * 1024 * 1024,
    })
    form.parse(req, (err, fields, files) => {
      if (err) return reject(err)
      resolve({ fields, files })
    })
  })
}

export const config = {
  api: {
    bodyParser: false,
  },
}

export default async function handler(req, res) {
  setCors(res, 'POST, OPTIONS')

  if (req.method === 'OPTIONS') {
    return res.status(200).end()
  }

  if (req.method !== 'POST') {
    return res.status(405).json({ error: 'Method not allowed' })
  }

  try {
    let extractedData = null
    const contentType = req.headers['content-type'] || ''

    if (contentType.includes('application/json')) {
      const chunks = []
      for await (const chunk of req) {
        chunks.push(chunk)
      }
      const body = JSON.parse(Buffer.concat(chunks).toString() || '{}')
      const text = body.text || ''
      if (!text.trim()) {
        return res.status(400).json({ error: 'No text provided' })
      }
      extractedData = await callGemini({
        parts: [{ text: `${getExtractionPrompt()}\n\nText to analyze:\n${text.trim()}` }],
        jsonMode: true,
      })
    } else if (contentType.includes('multipart/form-data')) {
      const { fields, files } = await parseForm(req)
      const text = Array.isArray(fields.text) ? fields.text[0] : fields.text
      const file = files.file ? (Array.isArray(files.file) ? files.file[0] : files.file) : null

      if (file && file.originalFilename) {
        const mimeType = file.mimetype || 'application/octet-stream'
        try {
          const base64Data = fs.readFileSync(file.filepath).toString('base64')
          extractedData = await callGemini({
            parts: [
              { text: getExtractionPrompt() },
              { inlineData: { mimeType, data: base64Data } },
            ],
            jsonMode: true,
          })
        } finally {
          try {
            fs.unlinkSync(file.filepath)
          } catch {
            // ignore
          }
        }
      } else if (text && text.trim()) {
        extractedData = await callGemini({
          parts: [{ text: `${getExtractionPrompt()}\n\nText to analyze:\n${text.trim()}` }],
          jsonMode: true,
        })
      } else {
        return res.status(400).json({ error: 'No text or file provided' })
      }
    } else {
      return res.status(400).json({ error: 'Unsupported content type' })
    }

    const userId = getUserId(req)
    await insertIntoSupabase(extractedData, userId)

    return res.status(200).json({
      success: true,
      message: 'Event processed successfully',
      event: extractedData,
    })
  } catch (error) {
    console.error('Process event error:', error)
    return res.status(500).json({
      error: error.message || 'Internal server error',
    })
  }
}
