import fs from 'fs'
import path from 'path'
import { IncomingForm } from 'formidable'
import {
  setCors,
  getUserId,
  getTimezoneContext,
  getSupabase,
  callGemini,
  buildExtractionPrompt,
  saveExtractedTasks,
} from '../_lib/chrona.js'

export const config = {
  api: {
    bodyParser: false,
  },
}

const EXT_TO_MIME = {
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.png': 'image/png',
  '.webp': 'image/webp',
  '.gif': 'image/gif',
  '.pdf': 'application/pdf',
  '.txt': 'text/plain',
  '.csv': 'text/csv',
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

function getFieldValue(field) {
  if (Array.isArray(field)) return field[0] || ''
  return typeof field === 'string' ? field : ''
}

function resolveMimeType(file) {
  const ext = path.extname(file.originalFilename || file.newFilename || '').toLowerCase()
  const rawMime = (file.mimetype || '').split(';')[0].trim().toLowerCase()
  if (rawMime && rawMime !== 'application/octet-stream') return rawMime
  return EXT_TO_MIME[ext] || 'image/jpeg'
}

export default async function handler(req, res) {
  setCors(res, 'POST, OPTIONS')

  if (req.method === 'OPTIONS') {
    return res.status(200).end()
  }

  if (req.method !== 'POST') {
    return res.status(405).json({ error: 'Method not allowed', detail: 'HTTP 405: Use POST' })
  }

  const userId = getUserId(req)
  const tzCtx = getTimezoneContext(req)
  const supabase = getSupabase()
  let tempPath = null

  try {
    const contentType = req.headers['content-type'] || ''
    if (!contentType.includes('multipart/form-data')) {
      return res.status(400).json({
        error: 'Expected multipart/form-data upload',
        detail: 'Expected multipart/form-data upload',
      })
    }

    const { fields, files } = await parseForm(req)
    const message = getFieldValue(fields.message).trim()
    const extractedText = getFieldValue(fields.extracted_text).trim()
    const rawFile = files.file
    const file = Array.isArray(rawFile) ? rawFile[0] : rawFile

    if (!file && !message && !extractedText) {
      return res.status(400).json({
        error: 'No file or message provided',
        detail: 'No file or message provided',
      })
    }

    let fileName = ''
    const parts = []
    const combinedNote = [message, extractedText].filter(Boolean).join('\n')
    const promptText = buildExtractionPrompt(tzCtx, combinedNote)

    if (file) {
      tempPath = file.filepath
      fileName = file.originalFilename || 'uploaded_file'
      const mimeType = resolveMimeType(file)
      const fileBuffer = fs.readFileSync(file.filepath)

      if (mimeType.startsWith('text/') || mimeType === 'application/csv') {
        const textContent = fileBuffer.toString('utf8')
        parts.push({
          text: `${promptText}\n\nUploaded file (${fileName}) contents:\n${textContent}`,
        })
      } else {
        parts.push({ text: promptText })
        parts.push({
          inlineData: {
            mimeType,
            data: fileBuffer.toString('base64'),
          },
        })
      }
    } else {
      parts.push({ text: promptText })
    }

    const parsed = await callGemini({ parts, jsonMode: true, perModelTimeoutMs: 18000 })
    const extractedTasks = Array.isArray(parsed?.tasks)
      ? parsed.tasks
      : Array.isArray(parsed)
      ? parsed
      : []

    const { saved: savedTasks, repeated: repeatedTasks } = await saveExtractedTasks(
      supabase,
      userId,
      extractedTasks,
      tzCtx
    )

    let finalMessage = parsed?.message || ''
    let action = 'clarification'

    if (savedTasks.length === 0 && repeatedTasks.length > 0) {
      const list = repeatedTasks.map((t) => `• ${t.title}`).join('\n')
      finalMessage = `🔁 Repeated task${repeatedTasks.length > 1 ? 's' : ''} detected from '${fileName || 'your input'}' — already on your schedule:\n${list}\n\nNo duplicate was added.`
      action = 'repeated_task'
    } else if (savedTasks.length > 0 && repeatedTasks.length > 0) {
      const names = repeatedTasks.map((t) => `"${t.title}"`).join(', ')
      finalMessage = `${finalMessage || `I've extracted and scheduled ${savedTasks.length} new item(s) from '${fileName || 'your input'}'.`}\n\n🔁 Skipped ${repeatedTasks.length} repeated task(s) already on your schedule: ${names}.`
      action = 'schedule_saved'
    } else if (savedTasks.length > 0) {
      finalMessage =
        finalMessage ||
        `I've extracted and scheduled ${savedTasks.length} item(s) from '${fileName || 'your input'}'.`
      action = 'schedule_saved'
    } else {
      finalMessage =
        finalMessage ||
        `I analyzed '${fileName || 'your input'}', but couldn't find clear schedulable events. Try adding a short note with the date or time.`
    }

    return res.status(200).json({
      message: finalMessage,
      action,
      tasks_extracted: savedTasks,
      tasks_saved: savedTasks,
      repeated_tasks: repeatedTasks,
      file_name: fileName,
      clarification_needed: parsed?.clarification_needed || null,
      schedule_trigger: savedTasks.length > 0,
      decomposition: null,
      quick_actions: [],
    })
  } catch (err) {
    console.error('api/chat/upload error:', err)
    return res.status(500).json({
      error: err.message || 'Failed to process uploaded file',
      detail: err.message || 'Failed to process uploaded file',
    })
  } finally {
    if (tempPath) {
      try {
        fs.unlinkSync(tempPath)
      } catch {
        // ignore temp cleanup failure
      }
    }
  }
}

