import { createWorker } from 'tesseract.js'
import path from 'path'
import { fileURLToPath } from 'url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const langPath = path.resolve(__dirname, '../public/tesseract')

const imagePath = process.argv[2]
if (!imagePath) {
  process.exit(1)
}

try {
  const worker = await createWorker('eng', 1, {
    langPath,
    gzip: true,
  })
  const ret = await worker.recognize(imagePath)
  await worker.terminate()
  process.stdout.write((ret.data && ret.data.text) ? ret.data.text.trim() : '')
} catch (err) {
  process.stderr.write(err.message || 'OCR Error')
  process.exit(1)
}
