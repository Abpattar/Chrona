import { GoogleGenerativeAI } from '@google/generative-ai';
import { IncomingForm } from 'formidable';
import fs from 'fs';

// ==============================
// CONFIGURATION
// ==============================

const GEMINI_API_KEY = process.env.GEMINI_API_KEY;
const SUPABASE_URL = process.env.SUPABASE_URL;
const SUPABASE_ANON_KEY = process.env.SUPABASE_ANON_KEY;

if (!GEMINI_API_KEY || !SUPABASE_URL || !SUPABASE_ANON_KEY) {
    console.warn('Missing environment variables for Gemini or Supabase.');
}

const genAI = new GoogleGenerativeAI(GEMINI_API_KEY);

// ==============================
// GEMINI PROMPT
// ==============================

function getExtractionPrompt() {
    const today = new Date().toISOString().split('T')[0];
    return `
You are an event extraction engine.

Analyze the input and extract event information.

Return JSON in this exact format:

{
  "title": "",
  "category": "",
  "venue": "",
  "event_datetime": ""
}

Rules:

category must be one of:
  exam
  hackathon
  assignment
  meeting
  personal
  reminder

Convert relative times using today's date (${today}):
  "today" → today's date
  "tomorrow" → tomorrow's date

Convert datetime to ISO format: YYYY-MM-DDTHH:MM:SS

Return ONLY valid JSON.
If a field is missing, return null for that field.
`;
}

// ==============================
// SUPABASE INSERT
// ==============================

async function insertIntoSupabase(eventData) {
    const cleanData = {};
    for (const key of ['title', 'category', 'venue', 'event_datetime']) {
        if (eventData[key] != null) {
            cleanData[key] = eventData[key];
        }
    }

    if (!cleanData.title) {
        throw new Error('Extracted event has no title');
    }

    const response = await fetch(`${SUPABASE_URL}/rest/v1/events`, {
        method: 'POST',
        headers: {
            apikey: SUPABASE_ANON_KEY,
            Authorization: `Bearer ${SUPABASE_ANON_KEY}`,
            'Content-Type': 'application/json',
            Prefer: 'return=minimal',
        },
        body: JSON.stringify(cleanData),
    });

    if (![200, 201, 204].includes(response.status)) {
        const text = await response.text();
        throw new Error(`Supabase error: ${text}`);
    }

    return true;
}

// ==============================
// GEMINI EXTRACTION
// ==============================

async function extractFromText(text) {
    const model = genAI.getGenerativeModel({ model: 'gemini-2.5-flash' });
    const prompt = `${getExtractionPrompt()}\n\nText to analyze:\n${text}`;
    const result = await model.generateContent({
        contents: [{ role: 'user', parts: [{ text: prompt }] }],
        generationConfig: { responseMimeType: 'application/json' },
    });
    return JSON.parse(result.response.text());
}

async function extractFromFile(filePath, mimeType) {
    const model = genAI.getGenerativeModel({ model: 'gemini-2.5-flash' });
    const fileBuffer = fs.readFileSync(filePath);
    const base64Data = fileBuffer.toString('base64');

    const result = await model.generateContent({
        contents: [
            {
                role: 'user',
                parts: [
                    { text: getExtractionPrompt() },
                    {
                        inlineData: {
                            mimeType: mimeType,
                            data: base64Data,
                        },
                    },
                ],
            },
        ],
        generationConfig: { responseMimeType: 'application/json' },
    });
    return JSON.parse(result.response.text());
}

// ==============================
// PARSE FORM DATA
// ==============================

function parseForm(req) {
    return new Promise((resolve, reject) => {
        const form = new IncomingForm({
            keepExtensions: true,
            maxFileSize: 10 * 1024 * 1024, // 10MB
        });
        form.parse(req, (err, fields, files) => {
            if (err) return reject(err);
            resolve({ fields, files });
        });
    });
}

// ==============================
// MAIN HANDLER
// ==============================

export const config = {
    api: {
        bodyParser: false, // Required for formidable to work
    },
};

export default async function handler(req, res) {
    // CORS headers
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'POST, OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type');

    if (req.method === 'OPTIONS') {
        return res.status(200).end();
    }

    if (req.method !== 'POST') {
        return res.status(405).json({ error: 'Method not allowed' });
    }

    try {
        let extractedData = null;
        const contentType = req.headers['content-type'] || '';

        if (contentType.includes('application/json')) {
            // JSON body (text-only)
            const chunks = [];
            for await (const chunk of req) {
                chunks.push(chunk);
            }
            const body = JSON.parse(Buffer.concat(chunks).toString());
            const text = body.text || '';

            if (!text.trim()) {
                return res.status(400).json({ error: 'No text provided' });
            }

            extractedData = await extractFromText(text.trim());
        } else if (contentType.includes('multipart/form-data')) {
            // FormData (file + optional text)
            const { fields, files } = await parseForm(req);

            const text = Array.isArray(fields.text) ? fields.text[0] : fields.text;
            const file = files.file ? (Array.isArray(files.file) ? files.file[0] : files.file) : null;

            if (file && file.originalFilename) {
                const mimeType = file.mimetype || 'application/octet-stream';
                try {
                    extractedData = await extractFromFile(file.filepath, mimeType);
                } finally {
                    // Clean up temp file
                    try { fs.unlinkSync(file.filepath); } catch (e) { /* ignore */ }
                }
            } else if (text && text.trim()) {
                extractedData = await extractFromText(text.trim());
            } else {
                return res.status(400).json({ error: 'No text or file provided' });
            }
        } else {
            return res.status(400).json({ error: 'Unsupported content type' });
        }

        // Insert into Supabase
        await insertIntoSupabase(extractedData);

        return res.status(200).json({
            success: true,
            message: 'Event processed successfully',
            event: extractedData,
        });
    } catch (error) {
        console.error('Process event error:', error);
        return res.status(500).json({
            error: error.message || 'Internal server error',
        });
    }
}
