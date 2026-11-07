import { useState, useRef, useEffect, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { api } from '../lib/api'
import dayjs from 'dayjs'
import Tesseract from 'tesseract.js'

const SUGGESTIONS = [
  'I have college from 9 AM to 4 PM every weekday',
  'I need to finish my project by Friday',
  'I have an exam next week — help me prepare',
  'I want to exercise 4 days a week',
  'I missed my study session today — reschedule it',
  'I have a flute class every Sunday at 5 PM',
]

function Message({ msg, onNavigateTimeline, onQuickAction }) {
  const isUser = msg.role === 'user'

  return (
    <div className={`flex gap-3 ${isUser ? 'flex-row-reverse' : 'flex-row'} animate-fade-in`}>
      {/* Avatar */}
      <div
        className="w-8 h-8 rounded-full flex items-center justify-center flex-shrink-0 text-xs font-bold shadow-md"
        style={{
          background: isUser
            ? 'linear-gradient(135deg, #4da3ff, #6366f1)'
            : 'linear-gradient(135deg, #3ddc97, #22c55e)',
        }}
      >
        {isUser ? 'U' : '✦'}
      </div>

      {/* Bubble */}
      <div
        className={`max-w-[85%] sm:max-w-[78%] rounded-2xl px-4 py-3 shadow-glass ${
          isUser ? 'rounded-tr-sm' : 'rounded-tl-sm'
        }`}
        style={{
          background: isUser ? 'rgba(77,163,255,0.14)' : 'rgba(15, 23, 42, 0.85)',
          border: `1px solid ${isUser ? 'rgba(77,163,255,0.25)' : 'rgba(255,255,255,0.08)'}`,
          backdropFilter: 'blur(8px)',
        }}
      >
        {/* Attached file badge in user message */}
        {msg.attachedFile && (
          <div className="mb-2 inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg text-xs font-mono bg-cyan-500/20 text-cyan-300 border border-cyan-500/30">
            <span>📄</span>
            <span className="font-semibold truncate max-w-[200px]">{msg.attachedFile}</span>
          </div>
        )}

        <p className="text-sm leading-relaxed whitespace-pre-wrap" style={{ color: 'var(--ch-text)' }}>
          {msg.content}
        </p>

        {/* Extracted tasks / commitments */}
        {msg.tasks_extracted?.length > 0 && (
          <div className="mt-3 pt-3" style={{ borderTop: '1px solid rgba(255,255,255,0.08)' }}>
            <div className="flex items-center justify-between mb-2">
              <p className="text-[10px] uppercase tracking-wider font-semibold text-emerald-400">
                ✦ Extracted Commitments ({msg.tasks_extracted.length})
              </p>
              {onNavigateTimeline && (
                <button
                  onClick={onNavigateTimeline}
                  className="text-[10px] text-cyan-400 hover:text-cyan-300 font-semibold underline underline-offset-2"
                >
                  View in Mind Map →
                </button>
              )}
            </div>
            <div className="space-y-1.5">
              {msg.tasks_extracted.map((t, i) => (
                <div
                  key={i}
                  className="flex items-center justify-between gap-2 text-xs px-2.5 py-1.5 rounded-lg bg-white/5 border border-white/5"
                >
                  <div className="flex items-center gap-2 min-w-0">
                    <span className="text-emerald-400 font-bold">✓</span>
                    <span className="truncate text-slate-200">{t.title}</span>
                  </div>
                  <div className="flex items-center gap-1.5 flex-shrink-0 text-[10px] font-mono">
                    {t.duration_minutes && <span className="text-slate-400">{t.duration_minutes}m</span>}
                    {t.task_type && (
                      <span className="px-1.5 py-0.5 rounded bg-cyan-500/15 text-cyan-300">
                        {t.task_type}
                      </span>
                    )}
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Quick action buttons (Decompression override / Confirmation) */}
        {msg.quick_actions && msg.quick_actions.length > 0 && (
          <div className="mt-3 pt-2.5 flex flex-wrap gap-2" style={{ borderTop: '1px solid rgba(255,255,255,0.08)' }}>
            {msg.quick_actions.map((qa, i) => (
              <button
                key={i}
                onClick={() => onQuickAction && onQuickAction(qa)}
                className="px-3 py-1.5 rounded-lg text-xs font-semibold transition-all border shadow-sm flex items-center gap-1.5 hover:scale-105 active:scale-95"
                style={{
                  background: qa.variant === 'warning' ? 'rgba(245, 158, 11, 0.18)' : qa.variant === 'primary' ? 'rgba(56, 189, 248, 0.18)' : 'rgba(255, 255, 255, 0.08)',
                  borderColor: qa.variant === 'warning' ? 'rgba(245, 158, 11, 0.45)' : qa.variant === 'primary' ? 'rgba(56, 189, 248, 0.45)' : 'rgba(255, 255, 255, 0.2)',
                  color: qa.variant === 'warning' ? '#fbbf24' : qa.variant === 'primary' ? '#7dd3fc' : '#cbd5e1',
                }}
              >
                <span>{qa.label}</span>
              </button>
            ))}
          </div>
        )}

        {/* Clarification prompt */}
        {msg.clarification_needed && (
          <div className="mt-2 p-2.5 rounded-lg text-xs bg-amber-500/10 border border-amber-500/25 text-amber-300">
            ❓ <span className="font-semibold">Missing Detail:</span> {msg.clarification_needed}
          </div>
        )}

        {/* Decomposition plan */}
        {msg.decomposition && (
          <div className="mt-3 pt-3" style={{ borderTop: '1px solid rgba(255,255,255,0.08)' }}>
            <p className="text-[10px] uppercase tracking-wider font-semibold text-cyan-400 mb-2">
              Project Plan ({msg.decomposition.subtasks?.length || 0} subtasks)
            </p>
            <div className="space-y-1">
              {msg.decomposition.subtasks?.slice(0, 6).map((s, i) => (
                <div key={i} className="flex items-center gap-2 text-xs py-1 px-2 rounded bg-white/5">
                  <span className="w-4 h-4 rounded-full flex items-center justify-center text-[9px] font-bold bg-cyan-500/20 text-cyan-300">
                    {s.order || i + 1}
                  </span>
                  <span className="text-slate-200 truncate flex-1">{s.title}</span>
                  <span className="text-[10px] text-slate-400 font-mono">{s.duration_minutes}m</span>
                </div>
              ))}
            </div>
          </div>
        )}

        <p className="text-[9px] mt-2 font-mono text-slate-500">
          {dayjs(msg.timestamp).format('h:mm A')}
        </p>
      </div>
    </div>
  )
}

export default function Chat() {
  const navigate = useNavigate()
  const [messages, setMessages] = useState([
    {
      id: '0',
      role: 'assistant',
      content:
        "Hi! I'm Chrona, your personal AI time-management assistant.\n\nYou can chat with me naturally, describe tasks, or upload your syllabus/timetable directly using the 📎 button or drag-and-drop.\n\nTry: \"I have college 9 AM to 4 PM every weekday, need to study OS for 2 hours daily, and exercise 4 days a week.\"",
      timestamp: new Date().toISOString(),
    },
  ])
  const [input, setInput] = useState('')
  const [loading, setLoading] = useState(false)
  const [attachedFile, setAttachedFile] = useState(null)
  const [dragActive, setDragActive] = useState(false)
  const [showUploadPanel, setShowUploadPanel] = useState(false)
  const [isListening, setIsListening] = useState(false)
  const [ocrStatus, setOcrStatus] = useState('')
  const [sessionId] = useState(() => `session-${Date.now()}`)

  // Upload drawer state
  const [panelText, setPanelText] = useState('')
  const [panelLoading, setPanelLoading] = useState(false)

  const bottomRef = useRef(null)
  const textareaRef = useRef(null)
  const fileInputRef = useRef(null)
  const speechRecognitionRef = useRef(null)

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  // Drag and drop handlers
  const handleDrag = (e) => {
    e.preventDefault()
    e.stopPropagation()
    if (e.type === 'dragenter' || e.type === 'dragover') {
      setDragActive(true)
    } else if (e.type === 'dragleave') {
      setDragActive(false)
    }
  }

  const handleDrop = (e) => {
    e.preventDefault()
    e.stopPropagation()
    setDragActive(false)
    if (e.dataTransfer.files?.[0]) {
      setAttachedFile(e.dataTransfer.files[0])
    }
  }

  // Voice recognition (Speech to text)
  const toggleSpeechRecognition = () => {
    const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition
    if (!SpeechRecognition) {
      alert('Speech recognition is not supported in this browser. Please use Chrome or Edge.')
      return
    }

    if (isListening) {
      speechRecognitionRef.current?.stop()
      setIsListening(false)
      return
    }

    const recognition = new SpeechRecognition()
    speechRecognitionRef.current = recognition
    recognition.continuous = false
    recognition.interimResults = false
    recognition.lang = 'en-US'

    recognition.onstart = () => setIsListening(true)
    recognition.onend = () => setIsListening(false)
    recognition.onerror = () => setIsListening(false)

    recognition.onresult = (event) => {
      const transcript = event.results[0][0].transcript
      setInput((prev) => (prev ? `${prev} ${transcript}` : transcript))
    }

    recognition.start()
  }

  // Send message or upload file
  const handleSend = useCallback(async () => {
    if ((!input.trim() && !attachedFile) || loading) return

    const currentText = input.trim()
    const currentFile = attachedFile

    let ocrText = ''
    if (currentFile && (currentFile.type?.startsWith('image/') || /\.(jpe?g|png|webp|bmp)$/i.test(currentFile.name || ''))) {
      setOcrStatus('Scanning timetable image with local OCR...')
      try {
        const worker = await Tesseract.createWorker('eng', 1, {
          workerPath: '/tesseract/worker.min.js',
          corePath: '/tesseract/tesseract-core.wasm.js',
          langPath: '/tesseract',
          gzip: true,
          logger: (m) => {
            if (m.status === 'recognizing text') {
              setOcrStatus(`Scanning image (${Math.round((m.progress || 0) * 100)}%)...`)
            }
          }
        })
        const ocrRes = await worker.recognize(currentFile)
        ocrText = (ocrRes?.data?.text || '').trim()
        await worker.terminate()
      } catch (err) {
        console.warn('OCR scan warning:', err)
      } finally {
        setOcrStatus('')
      }
    }

    const userMsg = {
      id: Date.now().toString(),
      role: 'user',
      content: currentText || (currentFile ? `Uploaded ${currentFile.name} for schedule extraction${ocrText ? ` (OCR extracted: "${ocrText.slice(0, 100).replace(/\s+/g, ' ')}...")` : ''}` : ''),
      attachedFile: currentFile ? currentFile.name : null,
      timestamp: new Date().toISOString(),
    }

    setMessages((prev) => [...prev, userMsg])
    setInput('')
    setAttachedFile(null)
    setLoading(true)

    try {
      let result
      if (currentFile) {
        const formData = new FormData()
        formData.append('file', currentFile)
        if (currentText) formData.append('message', currentText)
        if (ocrText) formData.append('extracted_text', ocrText)
        formData.append('session_id', sessionId)
        result = await api.chatUpload(formData)
      } else {
        result = await api.chat(currentText, sessionId)
      }

      const assistantMsg = {
        id: (Date.now() + 1).toString(),
        role: 'assistant',
        content: result.message || "I've analyzed your schedule commitments and placed them in your timeline.",
        timestamp: new Date().toISOString(),
        tasks_extracted: result.tasks_extracted || [],
        tasks_saved: result.tasks_saved || [],
        clarification_needed: result.clarification_needed,
        schedule_trigger: result.schedule_trigger,
        decomposition: result.decomposition,
        quick_actions: result.quick_actions || [],
      }
      setMessages((prev) => [...prev, assistantMsg])
    } catch (e) {
      setMessages((prev) => [
        ...prev,
        {
          id: (Date.now() + 1).toString(),
          role: 'assistant',
          content: `Error processing request: ${e.message}`,
          timestamp: new Date().toISOString(),
          isError: true,
        },
      ])
    } finally {
      setLoading(false)
    }
  }, [input, attachedFile, loading, sessionId])

  const handleQuickAction = async (qa) => {
    if (qa.action === 'override_break') {
      setLoading(true)
      try {
        const res = await api.overrideBreak({
          task_id: qa.task_id,
          task_title: qa.task_title,
          target_time: qa.target_time,
          skip_break: qa.skip_break,
        })
        const confirmMsg = {
          id: Date.now().toString(),
          role: 'assistant',
          content: res.message || `Schedule updated for '${qa.task_title}'.`,
          timestamp: new Date().toISOString(),
          tasks_saved: res.item ? [res.item] : [],
        }
        setMessages((prev) => [...prev, confirmMsg])
      } catch (err) {
        console.error('Quick action failed:', err)
      } finally {
        setLoading(false)
      }
    } else if (qa.action === 'keep_break' || qa.action === 'dismiss') {
      const confirmMsg = {
        id: Date.now().toString(),
        role: 'assistant',
        content: `Understood! Keeping the decompression break in place so you have time to recharge.`,
        timestamp: new Date().toISOString(),
      }
      setMessages((prev) => [...prev, confirmMsg])
    }
  }

  // Direct syllabus/document upload panel submit
  const handlePanelSubmit = async (e) => {
    e.preventDefault()
    if (!panelText.trim() && !attachedFile) return

    setPanelLoading(true)
    try {
      let res
      if (attachedFile) {
        const formData = new FormData()
        formData.append('file', attachedFile)
        if (attachedFile.type?.startsWith('image/') || /\.(jpe?g|png|webp|bmp)$/i.test(attachedFile.name || '')) {
          setOcrStatus('Scanning timetable image with local OCR...')
          try {
            const worker = await Tesseract.createWorker('eng', 1, {
              workerPath: '/tesseract/worker.min.js',
              corePath: '/tesseract/tesseract-core.wasm.js',
              langPath: '/tesseract',
              gzip: true,
            })
            const ocrRes = await worker.recognize(attachedFile)
            const ocrText = (ocrRes?.data?.text || '').trim()
            await worker.terminate()
            if (ocrText) {
              formData.append('extracted_text', ocrText)
              if (!panelText.trim()) formData.append('text', ocrText)
            }
          } catch (err) {
            console.warn('Panel OCR warning:', err)
          } finally {
            setOcrStatus('')
          }
        }
        if (panelText.trim()) formData.append('text', panelText.trim())
        res = await api.processEvent(formData)
      } else {
        res = await api.processEvent(panelText.trim())
      }

      const count = res.count || res.events?.length || 1
      setMessages((prev) => [
        ...prev,
        {
          id: Date.now().toString(),
          role: 'user',
          content: `Extracted from document:\n${panelText || attachedFile?.name}`,
          attachedFile: attachedFile?.name,
          timestamp: new Date().toISOString(),
        },
        {
          id: (Date.now() + 1).toString(),
          role: 'assistant',
          content: `✨ Successfully extracted ${count} commitments and added them to your schedule timeline!`,
          tasks_extracted: res.events || [],
          timestamp: new Date().toISOString(),
        },
      ])

      setPanelText('')
      setAttachedFile(null)
      setShowUploadPanel(false)
    } catch (err) {
      alert(`Upload error: ${err.message}`)
    } finally {
      setPanelLoading(false)
    }
  }

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      handleSend()
    }
  }

  return (
    <div
      className="flex-1 flex flex-col h-full overflow-hidden relative"
      onDragEnter={handleDrag}
      onDragOver={handleDrag}
      onDragLeave={handleDrag}
      onDrop={handleDrop}
    >
      {/* ── Drag & Drop Overlay ────────────────────────────────────── */}
      {dragActive && (
        <div className="absolute inset-0 z-50 flex items-center justify-center bg-[#070d1e]/90 backdrop-blur-md border-2 border-dashed border-cyan-400 pointer-events-none animate-fade-in">
          <div className="flex flex-col items-center gap-3 text-center px-4">
            <div className="w-16 h-16 rounded-2xl bg-cyan-500/20 flex items-center justify-center text-3xl shadow-glow">
              📄
            </div>
            <h3 className="text-xl font-bold text-white">Drop your timetable, syllabus, or schedule file</h3>
            <p className="text-sm text-cyan-300 font-mono">PDF, PNG, JPG, or Text supported</p>
          </div>
        </div>
      )}

      {/* ── Header with Upload Drawer Toggle ──────────────────────── */}
      <div className="flex-none px-4 py-2.5 glass-strong border-b border-white/5 flex items-center justify-between">
        <div className="flex items-center gap-2">
          <div className="w-2.5 h-2.5 rounded-full bg-emerald-400 animate-pulse" />
          <span className="text-sm font-bold text-white tracking-tight">Chrona AI Assistant</span>
          <span className="text-[10px] text-slate-400 font-mono hidden sm:inline">
            · Natural Language & Document Extraction
          </span>
        </div>

        <div className="flex items-center gap-2">
          <button
            onClick={() => setShowUploadPanel((v) => !v)}
            className={`text-xs px-3 py-1.5 rounded-xl font-semibold flex items-center gap-1.5 transition-all ${
              showUploadPanel
                ? 'bg-cyan-500/20 text-cyan-300 border border-cyan-500/40 shadow-sm'
                : 'bg-white/5 hover:bg-white/10 text-slate-300 border border-white/10'
            }`}
          >
            <span>{showUploadPanel ? '✕ Close' : '📄 Upload Document'}</span>
          </button>

          <button
            onClick={() => navigate('/calendar')}
            className="text-xs px-3 py-1.5 rounded-xl font-semibold bg-white/5 hover:bg-white/10 text-cyan-300 border border-white/10 flex items-center gap-1"
            title="Open Mind Map Timeline"
          >
            <span>🧠</span>
            <span className="hidden sm:inline">Timeline Map</span>
          </button>
        </div>
      </div>

      {/* ── Expandable Upload / Timetable Panel ─────────────────────── */}
      {showUploadPanel && (
        <div className="flex-none p-4 glass border-b border-cyan-500/20 animate-fade-in bg-[#0c142b]/95">
          <form onSubmit={handlePanelSubmit} className="space-y-3 max-w-2xl mx-auto">
            <div className="flex items-center justify-between">
              <h3 className="text-xs font-bold uppercase tracking-wider text-cyan-400 flex items-center gap-1.5">
                <span>⬆️</span> Quick Document / Syllabus Importer
              </h3>
              <span className="text-[10px] text-slate-400 font-mono">
                Extracted tasks automatically populate your Mind Map
              </span>
            </div>

            <textarea
              rows={3}
              value={panelText}
              onChange={(e) => setPanelText(e.target.value)}
              placeholder="Paste raw timetable, syllabus dates, or schedule text here... e.g. 'Operating Systems: Exam on Nov 15 at 10 AM, Project submission due Nov 20'"
              className="w-full text-xs p-3 rounded-xl bg-black/50 border border-white/10 text-white placeholder-slate-500 focus:outline-none focus:border-cyan-400 resize-none font-mono"
            />

            <div className="flex items-center justify-between gap-3">
              <div className="flex items-center gap-2">
                <input
                  type="file"
                  id="panelFileInput"
                  className="hidden"
                  onChange={(e) => {
                    if (e.target.files?.[0]) setAttachedFile(e.target.files[0])
                  }}
                />
                <label
                  htmlFor="panelFileInput"
                  className="text-xs px-3 py-1.5 rounded-lg bg-white/5 hover:bg-white/10 text-slate-300 border border-white/10 cursor-pointer flex items-center gap-1.5"
                >
                  <span>📎</span>
                  <span>{attachedFile ? attachedFile.name : 'Choose PDF / Image'}</span>
                </label>
                {attachedFile && (
                  <button
                    type="button"
                    onClick={() => setAttachedFile(null)}
                    className="text-xs text-red-400 hover:text-red-300"
                  >
                    ✕
                  </button>
                )}
              </div>

              <button
                type="submit"
                disabled={panelLoading || (!panelText.trim() && !attachedFile)}
                className="text-xs px-4 py-1.5 rounded-lg font-bold bg-gradient-to-r from-cyan-500 to-blue-600 text-white disabled:opacity-40 hover:brightness-110 shadow-glow"
              >
                {panelLoading ? 'Extracting with AI...' : 'Extract & Add to Timeline'}
              </button>
            </div>
          </form>
        </div>
      )}

      {/* ── Messages Stream ────────────────────────────────────────── */}
      <div className="flex-1 overflow-y-auto px-4 py-4 space-y-4">
        {messages.map((msg) => (
          <Message
            key={msg.id}
            msg={msg}
            onNavigateTimeline={() => navigate('/calendar')}
            onQuickAction={handleQuickAction}
          />
        ))}

        {loading && (
          <div className="flex gap-3 animate-fade-in">
            <div
              className="w-8 h-8 rounded-full flex items-center justify-center text-xs font-bold"
              style={{ background: 'linear-gradient(135deg, #3ddc97, #22c55e)' }}
            >
              ✦
            </div>
            <div
              className="rounded-2xl rounded-tl-sm px-4 py-3"
              style={{ background: 'rgba(255,255,255,0.04)', border: '1px solid rgba(255,255,255,0.06)' }}
            >
              <div className="flex gap-1.5 items-center">
                <div className="w-2 h-2 rounded-full animate-bounce bg-cyan-400" style={{ animationDelay: '0ms' }} />
                <div className="w-2 h-2 rounded-full animate-bounce bg-cyan-400" style={{ animationDelay: '150ms' }} />
                <div className="w-2 h-2 rounded-full animate-bounce bg-cyan-400" style={{ animationDelay: '300ms' }} />
              </div>
            </div>
          </div>
        )}

        <div ref={bottomRef} />
      </div>

      {/* ── Suggestions Chips ───────────────────────────────────────── */}
      {messages.length <= 2 && (
        <div className="px-4 pb-2 flex gap-2 overflow-x-auto scrollbar-none">
          {SUGGESTIONS.map((s, i) => (
            <button
              key={i}
              onClick={() => {
                setInput(s)
                textareaRef.current?.focus()
              }}
              className="flex-shrink-0 text-xs px-3 py-1.5 rounded-xl font-medium transition-all bg-white/5 hover:bg-white/10 border border-white/5 text-slate-300 hover:text-white"
            >
              {s}
            </button>
          ))}
        </div>
      )}

      {/* ── Bottom Input Bar with File Upload & Voice ───────────────── */}
      <div className="flex-shrink-0 px-4 pb-4">
        {/* Attachment Preview Chip */}
        {attachedFile && (
          <div className="mb-2 inline-flex items-center gap-2 px-3 py-1 rounded-xl bg-cyan-500/15 border border-cyan-500/30 text-cyan-300 text-xs animate-fade-in font-mono">
            <span>📄</span>
            <span className="font-semibold">{attachedFile.name}</span>
            <span className="text-[10px] text-slate-400">
              ({Math.round(attachedFile.size / 1024)} KB)
            </span>
            <button
              onClick={() => setAttachedFile(null)}
              className="ml-1 text-red-400 hover:text-red-300 font-bold"
            >
              ✕
            </button>
          </div>
        )}

        {/* OCR Scanning Status Indicator */}
        {ocrStatus && (
          <div className="mb-2 inline-flex items-center gap-2 px-3 py-1.5 rounded-xl bg-cyan-500/20 border border-cyan-500/40 text-cyan-300 text-xs animate-pulse font-mono">
            <div className="w-3.5 h-3.5 border-2 border-cyan-400 border-t-transparent rounded-full animate-spin" />
            <span>⚡ {ocrStatus}</span>
          </div>
        )}

        <div
          className="flex gap-2 items-end glass rounded-2xl p-2 shadow-glass"
          style={{ border: '1px solid rgba(77,163,255,0.25)' }}
        >
          {/* File attachment button */}
          <input
            type="file"
            ref={fileInputRef}
            className="hidden"
            accept=".pdf,.png,.jpg,.jpeg,.webp,.txt,.csv,.ics"
            onChange={(e) => {
              if (e.target.files?.[0]) setAttachedFile(e.target.files[0])
            }}
          />
          <button
            type="button"
            onClick={() => fileInputRef.current?.click()}
            className="w-9 h-9 rounded-xl flex items-center justify-center transition-all bg-white/5 hover:bg-white/10 text-cyan-300 border border-white/10 hover:border-cyan-400"
            title="Attach timetable, syllabus, or image"
          >
            <span className="text-base">📎</span>
          </button>

          {/* Speech-to-text mic button */}
          <button
            type="button"
            onClick={toggleSpeechRecognition}
            className={`w-9 h-9 rounded-xl flex items-center justify-center transition-all border ${
              isListening
                ? 'bg-red-500/20 text-red-400 border-red-500 animate-pulse'
                : 'bg-white/5 hover:bg-white/10 text-slate-300 border-white/10'
            }`}
            title={isListening ? 'Listening... click to stop' : 'Voice input'}
          >
            <span className="text-base">{isListening ? '🔴' : '🎙️'}</span>
          </button>

          {/* Textarea */}
          <textarea
            ref={textareaRef}
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder={
              attachedFile
                ? `Add notes about ${attachedFile.name} (optional)...`
                : 'Tell Chrona your commitments, upload a file, or ask to reschedule...'
            }
            rows={1}
            className="flex-1 bg-transparent resize-none text-sm px-2 py-2 focus:outline-none text-white placeholder-slate-500 max-h-32"
            onInput={(e) => {
              e.target.style.height = 'auto'
              e.target.style.height = `${Math.min(e.target.scrollHeight, 120)}px`
            }}
          />

          {/* Send button */}
          <button
            onClick={handleSend}
            disabled={loading || (!input.trim() && !attachedFile)}
            className="w-9 h-9 rounded-xl flex items-center justify-center transition-all disabled:opacity-40 hover:-translate-y-0.5 shadow-glow"
            style={{ background: 'linear-gradient(135deg, #4da3ff, #6366f1)' }}
          >
            <svg
              className="w-4 h-4 text-white"
              fill="none"
              viewBox="0 0 24 24"
              stroke="currentColor"
              strokeWidth={2}
            >
              <path strokeLinecap="round" strokeLinejoin="round" d="M12 19l9 2-9-18-9 18 9-2zm0 0v-8" />
            </svg>
          </button>
        </div>

        <p className="text-[10px] text-center mt-1.5 text-slate-500">
          Drop files directly · Press Enter to send · Auto-synced to your Mind Map
        </p>
      </div>
    </div>
  )
}
