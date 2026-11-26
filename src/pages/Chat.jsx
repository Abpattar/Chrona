import { useState, useRef, useEffect, useCallback, useMemo } from 'react'
import { useNavigate } from 'react-router-dom'
import { api } from '../lib/api'
import dayjs from 'dayjs'
import relativeTime from 'dayjs/plugin/relativeTime'

dayjs.extend(relativeTime)

// 5 Prompt Suggestion Pills exactly matching the reference image
const EXACT_COPILOT_PILLS = [
  { label: 'Learn something new', prompt: 'Teach me something new about optimal circadian time scheduling' },
  { label: 'Get advice', prompt: 'Analyze my schedule and give me advice on focus time and cognitive fatigue' },
  { label: 'Practice a language', prompt: 'Help me practice a language with a 15-minute scheduled daily session' },
  { label: 'Create a podcast', prompt: 'Plan an outline and record block for a new podcast episode' },
  { label: 'Make a plan', prompt: 'Make a structured study plan with 2-hour daily focus blocks' },
]

function getGreeting() {
  const hour = dayjs().hour()
  if (hour < 12) return 'Good morning'
  if (hour < 17) return 'Good afternoon'
  return 'Good evening'
}

function TaskCard({ task, onNavigateMap, onNavigateToday }) {
  const startDay = task.start_time || task.fixed_start ? dayjs(task.start_time || task.fixed_start) : null
  const endDay = task.end_time || task.fixed_end ? dayjs(task.end_time || task.fixed_end) : (startDay ? startDay.add(task.duration_minutes || 60, 'minute') : null)
  const isImmovable = task.movability === 'immovable' || task.flexibility === false || task.controllability === 'external'

  return (
    <div className="mt-3 p-4 rounded-[22px] relative overflow-hidden glass-copilot-light-card border border-white/80 shadow-md">
      <div className="flex items-center justify-between gap-2 mb-2">
        <div className="flex items-center gap-1.5">
          <span
            className="text-[9px] font-semibold uppercase tracking-wider px-2 py-0.5 rounded-full"
            style={{
              background: isImmovable ? 'rgba(15, 23, 42, 0.08)' : 'rgba(14, 165, 233, 0.12)',
              color: isImmovable ? '#0f172a' : '#0284c7',
              border: `1px solid ${isImmovable ? 'rgba(15, 23, 42, 0.15)' : 'rgba(14, 165, 233, 0.25)'}`,
            }}
          >
            {isImmovable ? 'Immovable' : 'Fluid Focus'}
          </span>
          <span className="text-[10px] font-mono text-emerald-600 font-semibold flex items-center gap-1">
            <span>●</span> Scheduled
          </span>
        </div>

        <span className="text-[10px] font-mono text-slate-500">
          {task.duration_minutes || 60}m duration
        </span>
      </div>

      <h3 className="text-sm sm:text-base font-bold tracking-tight text-slate-900 uppercase flex items-center gap-2">
        <span className="text-amber-500">◈</span>
        <span>{task.title}</span>
      </h3>

      {startDay ? (
        <div className="mt-2 flex items-center gap-2 flex-wrap text-xs font-mono">
          <div className="px-2.5 py-1 rounded-full bg-slate-100 border border-slate-200 text-slate-700 flex items-center gap-1.5 font-medium">
            <span>📅</span>
            <span>{startDay.format('dddd, MMMM D')}</span>
          </div>
          <div className="px-2.5 py-1 rounded-full bg-blue-50 border border-blue-200 text-blue-700 flex items-center gap-1.5 font-medium">
            <span>⏰</span>
            <span>{startDay.format('h:mm A')} → {endDay ? endDay.format('h:mm A') : ''}</span>
          </div>
        </div>
      ) : (
        <div className="mt-2 text-xs font-mono text-blue-600">
          ⏳ Flexible unassigned focus block — placed in schedule
        </div>
      )}

      {task.transition_reason && (
        <div className="mt-2.5 p-2 rounded-xl bg-amber-50 border border-amber-200 text-[11px] font-mono text-amber-800">
          ⚡ <span className="font-semibold text-amber-900">Buffer: </span>
          {task.transition_reason}
        </div>
      )}

      <div className="mt-3 pt-2.5 border-t border-slate-200/80 flex items-center gap-2 flex-wrap">
        {onNavigateMap && (
          <button
            onClick={onNavigateMap}
            className="px-3.5 py-1.5 rounded-full text-xs font-medium text-slate-700 bg-white hover:bg-slate-50 border border-slate-200 shadow-sm transition-all flex items-center gap-1.5 active:scale-95"
          >
            <span>🗺️</span>
            <span>Mind Map View</span>
          </button>
        )}
        {onNavigateToday && (
          <button
            onClick={onNavigateToday}
            className="px-3.5 py-1.5 rounded-full text-xs font-medium text-blue-700 bg-blue-50 hover:bg-blue-100 border border-blue-200 transition-all flex items-center gap-1.5 active:scale-95"
          >
            <span>◈</span>
            <span>View Today's Stack</span>
          </button>
        )}
      </div>
    </div>
  )
}

function MessageBubbleItem({ msg, onNavigateMap, onNavigateToday, onQuickAction }) {
  const isUser = msg.role === 'user'
  const allTasks = [...(msg.tasks_saved || []), ...(msg.tasks_extracted || [])]
  const uniqueTasks = []
  const seen = new Set()
  for (const t of allTasks) {
    const key = (t.id || t.title || '').toLowerCase()
    if (key && !seen.has(key)) {
      seen.add(key)
      uniqueTasks.push(t)
    }
  }
  const repeatedTasks = msg.repeated_tasks || []

  return (
    <div className={`flex gap-3 sm:gap-3.5 ${isUser ? 'flex-row-reverse' : 'flex-row'} animate-fade-in`}>
      {/* Avatar */}
      <div
        className="w-8 h-8 rounded-full flex items-center justify-center flex-shrink-0 text-xs font-bold shadow-md"
        style={{
          background: isUser ? '#1e293b' : 'linear-gradient(135deg, #2563eb, #0284c7)',
          border: '1px solid rgba(255, 255, 255, 0.8)',
          color: '#ffffff',
        }}
      >
        {isUser ? 'U' : '✧'}
      </div>

      <div
        className={`max-w-[88%] sm:max-w-[80%] rounded-[24px] p-4 sm:p-5 shadow-lg relative ${
          isUser
            ? 'rounded-tr-sm bg-white/95 text-slate-900 border border-white/80'
            : msg.isError
            ? 'rounded-tl-sm bg-rose-50/95 border border-rose-200 text-rose-900'
            : 'rounded-tl-sm glass-copilot-light-card text-slate-800'
        }`}
      >
        {msg.attachedFile && (
          <div className="mb-2.5 inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-mono bg-slate-100 text-slate-700 border border-slate-200">
            <span>📄</span>
            <span className="font-semibold truncate max-w-[240px]">{msg.attachedFile}</span>
          </div>
        )}

        <p className="text-sm leading-relaxed whitespace-pre-wrap font-normal">
          {msg.content}
        </p>

        {uniqueTasks.length > 0 && (
          <div className="mt-3 space-y-2.5">
            {uniqueTasks.map((task, idx) => (
              <TaskCard
                key={task.id || idx}
                task={task}
                onNavigateMap={onNavigateMap}
                onNavigateToday={onNavigateToday}
              />
            ))}
          </div>
        )}

        {repeatedTasks.length > 0 && (
          <div className="mt-3 p-2.5 rounded-xl bg-amber-50 border border-amber-200 space-y-1.5">
            <div className="text-[10px] font-mono uppercase tracking-wider text-amber-800 font-semibold">
              🔁 Already on your schedule ({repeatedTasks.length})
            </div>
            <div className="flex flex-wrap gap-1.5">
              {repeatedTasks.map((rt, idx) => (
                <span
                  key={rt.id || idx}
                  className="text-[11px] font-mono px-2 py-0.5 rounded-lg bg-white border border-amber-200 text-amber-800"
                >
                  {rt.title}
                  {rt.start_time ? ` · ${dayjs(rt.start_time).format('MMM D, h:mm A')}` : ''}
                </span>
              ))}
            </div>
          </div>
        )}

        {msg.quick_actions && msg.quick_actions.length > 0 && (
          <div className="mt-3.5 pt-2.5 flex flex-wrap gap-2 border-t border-slate-200/60">
            {msg.quick_actions.map((qa, i) => (
              <button
                key={i}
                onClick={() => onQuickAction && onQuickAction(qa)}
                className="px-3.5 py-1.5 rounded-full text-xs font-semibold tracking-wider transition-all border shadow-sm flex items-center gap-1.5 hover:scale-105 active:scale-95"
                style={{
                  background: qa.variant === 'warning' ? '#fef3c7' : '#e0f2fe',
                  borderColor: qa.variant === 'warning' ? '#fde68a' : '#bae6fd',
                  color: qa.variant === 'warning' ? '#92400e' : '#0369a1',
                }}
              >
                <span>{qa.label}</span>
              </button>
            ))}
          </div>
        )}

        {msg.clarification_needed && (
          <div className="mt-3 p-3 rounded-xl text-xs bg-amber-50 border border-amber-200 text-amber-800 font-mono">
            ⚠️ <span className="font-bold">Missing Detail:</span> {msg.clarification_needed}
          </div>
        )}

        <div className="text-[10px] font-mono text-slate-400 text-right mt-2">
          {dayjs(msg.timestamp).format('h:mm A')}
        </div>
      </div>
    </div>
  )
}

const CHAT_MESSAGES_KEY = 'chrona_chat_messages_v2'
const CHAT_SESSION_KEY = 'chrona_chat_session_id_v2'

const createWelcomeMessage = () => ({
  id: '0',
  role: 'assistant',
  content:
    "Hi, I'm Chrona — your adaptive AI time copilot.\n\nTell me about your commitments, deadlines, classes, or routines — or upload a timetable/document directly using 📎.\n\nI will intelligently schedule everything with realistic AI transition buffers and conflict resolution.",
  timestamp: new Date().toISOString(),
})

function loadStoredMessages() {
  try {
    const raw = localStorage.getItem(CHAT_MESSAGES_KEY)
    if (raw) {
      const parsed = JSON.parse(raw)
      if (Array.isArray(parsed) && parsed.length > 0) return parsed
    }
  } catch {}
  return [createWelcomeMessage()]
}

export default function Chat() {
  const navigate = useNavigate()
  const [messages, setMessages] = useState(loadStoredMessages)
  const [input, setInput] = useState('')
  const [loading, setLoading] = useState(false)
  const [attachedFile, setAttachedFile] = useState(null)
  const [dragActive, setDragActive] = useState(false)
  const [isListening, setIsListening] = useState(false)
  const [viewMode, setViewMode] = useState('hub') // 'hub' | 'chat'
  const [sessionId, setSessionId] = useState(() => localStorage.getItem(CHAT_SESSION_KEY) || `session-${Date.now()}`)

  const bottomRef = useRef(null)
  const fileInputRef = useRef(null)
  const speechRecognitionRef = useRef(null)
  const greeting = useMemo(() => getGreeting(), [])

  // Extract unique recently typed user messages
  const recentUserQueries = useMemo(() => {
    const list = []
    const seen = new Set()
    for (let i = messages.length - 1; i >= 0; i--) {
      const m = messages[i]
      if (m.role === 'user' && m.content) {
        const text = m.content.trim()
        if (text && !seen.has(text.toLowerCase())) {
          seen.add(text.toLowerCase())
          list.push({
            text,
            timeAgo: m.timestamp ? dayjs(m.timestamp).fromNow() : 'Recently',
          })
          if (list.length >= 3) break
        }
      }
    }
    // Default schedule prompt fallbacks if fewer than 3 typed queries
    const fallbacks = [
      { text: 'I have a family meeting tomorrow at 5pm', timeAgo: 'Earlier' },
      { text: 'Auto-reschedule missed sessions', timeAgo: 'Earlier' },
      { text: 'Plan 2-hour daily focus blocks', timeAgo: 'Earlier' },
    ]
    for (const f of fallbacks) {
      if (list.length >= 3) break
      if (!seen.has(f.text.toLowerCase())) {
        list.push(f)
      }
    }
    return list
  }, [messages])

  useEffect(() => {
    try {
      localStorage.setItem(CHAT_MESSAGES_KEY, JSON.stringify(messages.slice(-80)))
    } catch {}
  }, [messages])

  useEffect(() => {
    if (viewMode === 'chat') {
      bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
    }
  }, [messages, loading, viewMode])

  const handleClearChat = async () => {
    const freshSession = `session-${Date.now()}`
    const freshWelcome = [createWelcomeMessage()]
    setMessages(freshWelcome)
    setSessionId(freshSession)
    try {
      localStorage.setItem(CHAT_MESSAGES_KEY, JSON.stringify(freshWelcome))
      localStorage.setItem(CHAT_SESSION_KEY, freshSession)
    } catch {}
    api.clearChatHistory(sessionId).catch(() => {})
  }

  const handleDrag = (e) => {
    e.preventDefault()
    e.stopPropagation()
    if (e.type === 'dragenter' || e.type === 'dragover') setDragActive(true)
    else if (e.type === 'dragleave') setDragActive(false)
  }

  const handleDrop = (e) => {
    e.preventDefault()
    e.stopPropagation()
    setDragActive(false)
    if (e.dataTransfer.files?.[0]) setAttachedFile(e.dataTransfer.files[0])
  }

  const toggleSpeech = () => {
    const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition
    if (!SpeechRecognition) {
      alert('Speech recognition is not supported in this browser.')
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

  const handleSend = useCallback(async (overrideText = null) => {
    const textToSend = (overrideText !== null ? overrideText : input).trim()
    const fileToSend = attachedFile

    if ((!textToSend && !fileToSend) || loading) return

    const userMsg = {
      id: Date.now().toString(),
      role: 'user',
      content: textToSend || (fileToSend ? `Provided ${fileToSend.name} for schedule extraction` : ''),
      attachedFile: fileToSend ? fileToSend.name : null,
      timestamp: new Date().toISOString(),
    }

    setMessages((prev) => [...prev, userMsg])
    setInput('')
    setAttachedFile(null)
    setLoading(true)
    setViewMode('chat')

    try {
      let result
      if (fileToSend) {
        const formData = new FormData()
        formData.append('file', fileToSend)
        if (textToSend) formData.append('message', textToSend)
        formData.append('session_id', sessionId)
        result = await api.chatUpload(formData)
      } else {
        result = await api.chat(textToSend, sessionId)
      }

      const assistantMsg = {
        id: (Date.now() + 1).toString(),
        role: 'assistant',
        content: result.message || "Your schedule has been updated.",
        timestamp: new Date().toISOString(),
        tasks_extracted: result.tasks_extracted || [],
        tasks_saved: result.tasks_saved || [],
        repeated_tasks: result.repeated_tasks || [],
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
          content: `Error: ${e.message}`,
          timestamp: new Date().toISOString(),
          isError: true,
        },
      ])
    } finally {
      setLoading(false)
    }
  }, [input, attachedFile, loading, sessionId])

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      handleSend()
    }
  }

  return (
    <div
      className="flex-1 flex flex-col h-full overflow-hidden relative bg-copilot-daylight select-none"
      onDragEnter={handleDrag}
      onDragOver={handleDrag}
      onDragLeave={handleDrag}
      onDrop={handleDrop}
    >
      {/* ── Realistic Daylight Floral Sky Backdrop (Exact Match to Reference Photo) ─ */}
      <div className="absolute inset-0 pointer-events-none overflow-hidden -z-10">
        {/* Soft atmospheric blue daylight gradient */}
        <div className="absolute inset-0 bg-gradient-to-b from-[#2a6ea7] via-[#468ec6] to-[#88c0e8]" />

        {/* Golden sunlit horizon glow */}
        <div className="absolute bottom-0 inset-x-0 h-80 bg-gradient-to-t from-[#fed7aa]/35 via-[#fef08a]/20 to-transparent blur-3xl" />

        {/* Soft floral cosmos & daisy bokeh silhouettes at bottom edge */}
        <div className="absolute -bottom-6 inset-x-0 h-48 flex items-end justify-between px-6 pointer-events-none opacity-85">
          {/* Flower cluster left */}
          <div className="relative w-48 h-36">
            <div className="absolute bottom-4 left-6 w-16 h-16 rounded-full bg-white/70 blur-md shadow-2xl" />
            <div className="absolute bottom-8 left-10 w-6 h-6 rounded-full bg-amber-400 blur-sm" />
            <div className="absolute bottom-2 left-20 w-14 h-14 rounded-full bg-white/60 blur-md" />
            <div className="absolute bottom-6 left-24 w-5 h-5 rounded-full bg-orange-400 blur-sm" />
          </div>

          {/* Flower cluster center */}
          <div className="relative w-64 h-40">
            <div className="absolute bottom-5 left-16 w-20 h-20 rounded-full bg-white/75 blur-md" />
            <div className="absolute bottom-11 left-22 w-8 h-8 rounded-full bg-amber-400 blur-xs" />
            <div className="absolute bottom-3 left-32 w-16 h-16 rounded-full bg-[#fde68a]/80 blur-md" />
            <div className="absolute bottom-8 left-36 w-7 h-7 rounded-full bg-orange-400 blur-xs" />
          </div>

          {/* Flower cluster right */}
          <div className="relative w-56 h-36">
            <div className="absolute bottom-4 right-8 w-18 h-18 rounded-full bg-white/70 blur-md" />
            <div className="absolute bottom-10 right-14 w-7 h-7 rounded-full bg-amber-400 blur-xs" />
            <div className="absolute bottom-2 right-24 w-14 h-14 rounded-full bg-white/60 blur-md" />
          </div>
        </div>
      </div>

      {/* File Drop Overlay */}
      {dragActive && (
        <div className="absolute inset-0 z-50 flex items-center justify-center bg-white/90 backdrop-blur-md border-2 border-dashed border-blue-500 pointer-events-none animate-fade-in">
          <div className="flex flex-col items-center gap-3 text-center px-4">
            <div className="w-16 h-16 rounded-3xl bg-blue-100 border border-blue-300 flex items-center justify-center text-3xl shadow-xl">
              📄
            </div>
            <h3 className="text-xl font-bold text-slate-800 tracking-tight">
              Drop Timetable or Document
            </h3>
            <p className="text-xs text-blue-600 font-mono">PDF, PNG, JPG, or Text supported</p>
          </div>
        </div>
      )}

      {/* ── Main Viewport (Screen-Fit Guaranteed, No Scrollbar) ───────────────── */}
      {viewMode === 'hub' ? (
        /* ═══════════════════════════════════════════════════════════════════════
           EXACT REPLICA OF THE UPLOADED WINDOWS COPILOT DAYLIGHT FLORA UI
        ═══════════════════════════════════════════════════════════════════════ */
        <div className="flex-1 min-h-0 overflow-hidden scrollbar-none px-4 sm:px-6 py-2 sm:py-3 max-w-5xl mx-auto w-full flex flex-col justify-between animate-fade-in">

          {/* Section 1: Greeting Exactly Matching Image ("Good morning, Charles / What's on your mind?") */}
          <div className="text-center pt-0.5 sm:pt-1 pb-0.5">
            <h1 className="text-xl sm:text-2xl md:text-3xl font-medium tracking-tight text-white drop-shadow-[0_2px_8px_rgba(0,0,0,0.25)]">
              {greeting}, Charles
            </h1>
            <p className="text-base sm:text-xl font-normal text-white drop-shadow-[0_2px_8px_rgba(0,0,0,0.25)] mt-0.5">
              What's on your mind?
            </p>
          </div>

          {/* Section 2: Floating Center Input Capsule (Exact Match) */}
          <div className="w-full max-w-2xl mx-auto flex flex-col items-center">
            
            {attachedFile && (
              <div className="mb-1 self-start inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full bg-white/80 border border-white text-slate-800 text-[11px] font-mono shadow-sm animate-fade-in">
                <span>📄</span>
                <span className="font-semibold truncate max-w-[200px]">{attachedFile.name}</span>
                <button
                  onClick={() => setAttachedFile(null)}
                  className="ml-1 text-slate-500 hover:text-slate-800 font-bold"
                >
                  ✕
                </button>
              </div>
            )}

            {/* White Frosted Capsule Container */}
            <div className="w-full rounded-[22px] sm:rounded-[26px] p-2.5 sm:p-3 glass-copilot-light-input flex flex-col justify-between gap-1.5 transition-all shadow-lg">
              {/* Input Area with placeholder 'Message Copilot' and blinking cursor */}
              <div className="flex items-center">
                <input
                  type="text"
                  value={input}
                  onChange={(e) => setInput(e.target.value)}
                  onKeyDown={handleKeyDown}
                  placeholder={attachedFile ? 'Add notes for this file...' : 'Message Copilot'}
                  className="w-full bg-transparent text-sm sm:text-base text-slate-900 placeholder-slate-400 focus:outline-none font-normal leading-normal"
                />
              </div>

              {/* Bottom Row Inside Capsule: Left (+, Quick response ⌵), Right (glasses, mic) */}
              <div className="flex items-center justify-between pt-0.5">
                <div className="flex items-center gap-1.5 sm:gap-2">
                  <input
                    ref={fileInputRef}
                    type="file"
                    className="hidden"
                    accept=".pdf,.png,.jpg,.jpeg,.txt,.csv"
                    onChange={(e) => {
                      if (e.target.files?.[0]) setAttachedFile(e.target.files[0])
                    }}
                  />
                  <button
                    type="button"
                    onClick={() => fileInputRef.current?.click()}
                    title="Attach document or timetable"
                    className="w-7 h-7 sm:w-8 sm:h-8 rounded-full flex items-center justify-center text-slate-600 hover:text-slate-900 hover:bg-slate-100 transition-all text-lg font-light"
                  >
                    +
                  </button>

                  {/* Pill Dropdown: 'Quick response ⌵' */}
                  <div
                    onClick={() => handleSend('Auto-balance and schedule focus time')}
                    className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-white/70 hover:bg-white border border-slate-200 text-xs font-medium text-slate-700 shadow-xs transition-all cursor-pointer"
                  >
                    <span>Quick response</span>
                    <span className="text-[9px] text-slate-500">⌵</span>
                  </div>
                </div>

                <div className="flex items-center gap-1 sm:gap-1.5">
                  <button
                    type="button"
                    onClick={() => handleSend('Deep reasoning on schedule conflicts')}
                    title="Reasoning / Vision"
                    className="w-7 h-7 sm:w-8 sm:h-8 rounded-full flex items-center justify-center text-slate-600 hover:text-slate-900 hover:bg-slate-100 transition-all text-sm"
                  >
                    ⚯
                  </button>

                  <button
                    type="button"
                    onClick={toggleSpeech}
                    title={isListening ? 'Stop listening' : 'Voice Input'}
                    className={`w-7 h-7 sm:w-8 sm:h-8 rounded-full flex items-center justify-center transition-all text-sm ${
                      isListening
                        ? 'text-amber-600 bg-amber-100 animate-pulse ring-1 ring-amber-400'
                        : 'text-slate-600 hover:text-slate-900 hover:bg-slate-100'
                    }`}
                  >
                    🎙️
                  </button>

                  {input.trim() && (
                    <button
                      type="button"
                      onClick={() => handleSend()}
                      disabled={loading}
                      className="ml-1 w-7 h-7 rounded-full bg-blue-600 text-white flex items-center justify-center text-xs shadow-md active:scale-95 transition-transform"
                    >
                      →
                    </button>
                  )}
                </div>
              </div>
            </div>

            {/* Section 3: Exactly the 5 Pill Suggestion Chips from Image in ONE Neat Row */}
            <div className="mt-2 sm:mt-2.5 flex flex-wrap sm:flex-nowrap items-center justify-center gap-1.5 sm:gap-2 max-w-4xl w-full">
              {EXACT_COPILOT_PILLS.map((item, idx) => (
                <button
                  key={idx}
                  onClick={() => handleSend(item.prompt)}
                  className="px-3 sm:px-3.5 py-1 rounded-full text-[11px] sm:text-xs font-medium text-slate-800 glass-copilot-light-pill whitespace-nowrap transition-all"
                >
                  {item.label}
                </button>
              ))}
            </div>

          </div>

          {/* Section 4: Exactly the 3 Bottom Cards from Reference Image (Compact Viewport Fit) */}
          <div className="grid grid-cols-1 md:grid-cols-3 gap-2 sm:gap-3 mt-1 sm:mt-2">
            
            {/* Card 1: 📎 Quick Schedule Plans (Family Meeting, Exam Prep, Timetable) */}
            <div className="rounded-[18px] sm:rounded-[22px] p-2 sm:p-2.5 glass-copilot-light-card relative overflow-hidden flex flex-col justify-between shadow-xs">
              {/* Warm sunset flora reflection on bottom-right corner */}
              <div className="absolute -bottom-8 -right-8 w-24 h-24 rounded-full bg-amber-400/25 blur-xl pointer-events-none" />

              <div className="relative z-10">
                <div className="flex items-center justify-between text-xs text-slate-700 mb-1.5 font-medium">
                  <span className="flex items-center gap-1.5">
                    <span>📎</span>
                    <span>Jump back in to your plans</span>
                  </span>
                  <span className="text-slate-400 text-xs cursor-pointer hover:text-slate-600">ⓘ</span>
                </div>

                <div className="space-y-1">
                  {/* Item 1: Family Meeting */}
                  <div
                    onClick={() => handleSend('I have a family meeting tomorrow at 5pm')}
                    className="flex items-center justify-between p-1 rounded-lg hover:bg-white/60 cursor-pointer transition-colors group"
                  >
                    <div className="flex items-center gap-2 min-w-0">
                      <div className="w-6 h-6 rounded-md bg-blue-100 border border-blue-200 flex items-center justify-center text-xs flex-shrink-0 text-blue-600 font-bold">
                        👥
                      </div>
                      <div className="min-w-0">
                        <p className="text-xs font-semibold text-slate-800 group-hover:text-blue-600 truncate">
                          Family Meeting
                        </p>
                        <p className="text-[10px] text-slate-500 font-mono leading-tight">Tomorrow at 5:00 PM · Immovable</p>
                      </div>
                    </div>
                    <span className="text-slate-400 text-xs">•••</span>
                  </div>

                  {/* Item 2: Exam Preparation */}
                  <div
                    onClick={() => handleSend('I have an exam next week — help me prepare with scheduled study blocks')}
                    className="flex items-center justify-between p-1 rounded-lg hover:bg-white/60 cursor-pointer transition-colors group"
                  >
                    <div className="flex items-center gap-2 min-w-0">
                      <div className="w-6 h-6 rounded-md bg-amber-100 border border-amber-200 flex items-center justify-center text-xs flex-shrink-0 text-amber-700 font-bold">
                        📖
                      </div>
                      <div className="min-w-0">
                        <p className="text-xs font-semibold text-slate-800 group-hover:text-amber-700 truncate">
                          Exam Preparation
                        </p>
                        <p className="text-[10px] text-slate-500 font-mono leading-tight">Structured daily study blocks</p>
                      </div>
                    </div>
                    <span className="text-slate-400 text-xs">•••</span>
                  </div>

                  {/* Item 3: College Timetable */}
                  <div
                    onClick={() => handleSend('I have college 9 AM to 4 PM every weekday, and need to study for 2 hours daily')}
                    className="flex items-center justify-between p-1 rounded-lg hover:bg-white/60 cursor-pointer transition-colors group"
                  >
                    <div className="flex items-center gap-2 min-w-0">
                      <div className="w-6 h-6 rounded-md bg-emerald-100 border border-emerald-200 flex items-center justify-center text-xs flex-shrink-0 text-emerald-700 font-bold">
                        🏫
                      </div>
                      <div className="min-w-0">
                        <p className="text-xs font-semibold text-slate-800 group-hover:text-emerald-700 truncate">
                          College Timetable
                        </p>
                        <p className="text-[10px] text-slate-500 font-mono leading-tight">Weekdays 9 AM – 4 PM · Syllabus</p>
                      </div>
                    </div>
                    <span className="text-slate-400 text-xs">•••</span>
                  </div>
                </div>
              </div>
            </div>

            {/* Card 2: ⚯ Get guided help with your apps (Exact replica) */}
            <div className="rounded-[18px] sm:rounded-[22px] p-2 sm:p-2.5 glass-copilot-light-card relative overflow-hidden flex flex-col justify-between shadow-xs">
              {/* Warm flora reflection on bottom-right corner */}
              <div className="absolute -bottom-8 -right-8 w-24 h-24 rounded-full bg-amber-400/25 blur-xl pointer-events-none" />

              <div className="relative z-10">
                <div className="flex items-center justify-between text-xs text-slate-700 mb-1.5 font-medium">
                  <span className="flex items-center gap-1.5">
                    <span>⚯</span>
                    <span>Get guided help with your apps</span>
                  </span>
                  <span className="text-slate-400 text-xs cursor-pointer hover:text-slate-600">ⓘ</span>
                </div>

                {/* 6 Clean App Tiles in 3x2 Grid */}
                <div className="grid grid-cols-3 gap-1.5 py-0.5">
                  <button
                    onClick={() => navigate('/calendar')}
                    className="p-1 sm:p-1.5 rounded-xl bg-white/60 hover:bg-white border border-white flex flex-col items-center justify-center gap-0.5 shadow-xs transition-all active:scale-95 group"
                    title="Mind Map Schedule"
                  >
                    <div className="w-6 h-6 rounded-md bg-blue-500 text-white flex items-center justify-center text-xs font-bold shadow-xs group-hover:scale-105 transition-transform">
                      O
                    </div>
                    <span className="text-[9px] font-medium text-slate-700">Mindmap</span>
                  </button>

                  <button
                    onClick={() => navigate('/week')}
                    className="p-1 sm:p-1.5 rounded-xl bg-white/60 hover:bg-white border border-white flex flex-col items-center justify-center gap-0.5 shadow-xs transition-all active:scale-95 group"
                    title="Week Timeline"
                  >
                    <div className="w-6 h-6 rounded-md bg-blue-700 text-white flex items-center justify-center text-xs font-bold shadow-xs group-hover:scale-105 transition-transform">
                      W
                    </div>
                    <span className="text-[9px] font-medium text-slate-700">Week Grid</span>
                  </button>

                  <button
                    onClick={() => navigate('/dashboard')}
                    className="p-1 sm:p-1.5 rounded-xl bg-white/60 hover:bg-white border border-white flex flex-col items-center justify-center gap-0.5 shadow-xs transition-all active:scale-95 group"
                    title="Today's 3D Deck"
                  >
                    <div className="w-6 h-6 rounded-md bg-emerald-600 text-white flex items-center justify-center text-xs font-bold shadow-xs group-hover:scale-105 transition-transform">
                      X
                    </div>
                    <span className="text-[9px] font-medium text-slate-700">Today Deck</span>
                  </button>

                  <button
                    onClick={() => handleSend('Auto-reschedule all missed tasks')}
                    className="p-1 sm:p-1.5 rounded-xl bg-white/60 hover:bg-white border border-white flex flex-col items-center justify-center gap-0.5 shadow-xs transition-all active:scale-95 group"
                    title="Auto-Reschedule"
                  >
                    <div className="w-6 h-6 rounded-md bg-orange-600 text-white flex items-center justify-center text-xs font-bold shadow-xs group-hover:scale-105 transition-transform">
                      P
                    </div>
                    <span className="text-[9px] font-medium text-slate-700">Rebalance</span>
                  </button>

                  <button
                    onClick={() => handleSend('I want to start a 45 minute deep focus block right now')}
                    className="p-1 sm:p-1.5 rounded-xl bg-white/60 hover:bg-white border border-white flex flex-col items-center justify-center gap-0.5 shadow-xs transition-all active:scale-95 group"
                    title="Focus Sprint"
                  >
                    <div className="w-6 h-6 rounded-md bg-indigo-600 text-white flex items-center justify-center text-xs font-bold shadow-xs group-hover:scale-105 transition-transform">
                      T
                    </div>
                    <span className="text-[9px] font-medium text-slate-700">Focus</span>
                  </button>

                  <button
                    onClick={() => handleSend('Explain Chrona AI dynamic scheduling engine')}
                    className="p-1 sm:p-1.5 rounded-xl bg-white/60 hover:bg-white border border-white flex flex-col items-center justify-center gap-0.5 shadow-xs transition-all active:scale-95 group"
                    title="AI Engine"
                  >
                    <div className="w-6 h-6 rounded-md bg-cyan-600 text-white flex items-center justify-center text-xs font-bold shadow-xs group-hover:scale-105 transition-transform">
                      e
                    </div>
                    <span className="text-[9px] font-medium text-slate-700">AI Core</span>
                  </button>
                </div>
              </div>
            </div>

            {/* Card 3: 💬 Recent Prompts (Recently typed queries by user) */}
            <div className="rounded-[18px] sm:rounded-[22px] p-2 sm:p-2.5 glass-copilot-light-card relative overflow-hidden flex flex-col justify-between shadow-xs">
              {/* Warm flora reflection on bottom-right corner */}
              <div className="absolute -bottom-8 -right-8 w-24 h-24 rounded-full bg-amber-400/25 blur-xl pointer-events-none" />

              <div className="relative z-10">
                <div className="flex items-center justify-between text-xs text-slate-700 mb-1.5 font-medium">
                  <span className="flex items-center gap-1.5">
                    <span>💬</span>
                    <span>Recently typed prompts</span>
                  </span>
                  <span className="text-slate-400 text-xs cursor-pointer hover:text-slate-600">ⓘ</span>
                </div>

                <div className="space-y-1">
                  {recentUserQueries.map((item, idx) => (
                    <div
                      key={idx}
                      onClick={() => handleSend(item.text)}
                      className="flex items-center justify-between p-1 rounded-lg hover:bg-white/60 cursor-pointer transition-colors group"
                      title={item.text}
                    >
                      <div className="flex items-center gap-2 min-w-0">
                        <div className="w-6 h-6 rounded-full bg-slate-100 flex items-center justify-center text-xs flex-shrink-0 text-slate-600">
                          💬
                        </div>
                        <div className="min-w-0">
                          <p className="text-xs font-semibold text-slate-800 group-hover:text-blue-600 truncate">
                            {item.text}
                          </p>
                          <p className="text-[10px] text-slate-500 font-mono leading-tight">{item.timeAgo}</p>
                        </div>
                      </div>
                      <span className="text-slate-400 text-xs">•••</span>
                    </div>
                  ))}
                </div>
              </div>
            </div>

          </div>

        </div>
      ) : (
        /* ═══════════════════════════════════════════════════════════════════════
           CONVERSATION STREAM IN CLEAN DAYLIGHT GLASS AESTHETIC
        ═══════════════════════════════════════════════════════════════════════ */
        <div className="flex-1 flex flex-col min-h-0 overflow-hidden animate-fade-in">
          
          {/* Header in Chat Mode with Back to Hub button */}
          <div className="flex-none pt-2 px-4 max-w-3xl mx-auto w-full flex items-center justify-between z-20">
            <button
              onClick={() => setViewMode('hub')}
              className="px-3.5 py-1 rounded-full text-xs font-medium text-slate-800 bg-white/70 hover:bg-white border border-white/80 shadow-xs transition-all flex items-center gap-1.5 active:scale-95"
            >
              <span>←</span>
              <span>Back to Hub</span>
            </button>

            {messages.length > 1 && (
              <button
                onClick={handleClearChat}
                className="px-3 py-1 rounded-full text-[11px] font-mono text-slate-700 hover:text-rose-700 bg-white/40 hover:bg-white/70 border border-white/50 backdrop-blur-md transition-all shadow-xs"
                title="Clear Conversation"
              >
                Reset Session
              </button>
            )}
          </div>
          
          <div className="flex-1 min-h-0 overflow-y-auto px-3 sm:px-6 py-4 max-w-3xl mx-auto w-full space-y-4">
            {messages.map((m) => (
              <MessageBubbleItem
                key={m.id}
                msg={m}
                onNavigateMap={() => navigate('/calendar')}
                onNavigateToday={() => navigate('/dashboard')}
                onQuickAction={(qa) => handleSend(qa.prompt || qa.label)}
              />
            ))}

            {loading && (
              <div className="flex gap-2.5 animate-fade-in">
                <div className="w-8 h-8 rounded-full bg-blue-600 text-white flex items-center justify-center text-xs shadow-md flex-shrink-0">
                  ✧
                </div>
                <div className="rounded-2xl rounded-tl-sm px-4 py-3 glass-copilot-light-card border border-white/80 flex items-center gap-2.5 shadow-sm">
                  <div className="w-2 h-2 rounded-full bg-blue-600 animate-ping" />
                  <span className="text-xs text-slate-700 tracking-wide font-mono">
                    Reasoning schedule & calculating transition buffers...
                  </span>
                </div>
              </div>
            )}

            <div ref={bottomRef} />
          </div>

          {/* Floating Bottom Input Dock in Chat Mode */}
          <div className="flex-none p-3 max-w-3xl mx-auto w-full">
            {attachedFile && (
              <div className="mb-2 inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-white border border-slate-200 text-slate-800 text-xs font-mono shadow-sm animate-fade-in">
                <span>📄</span>
                <span className="font-semibold truncate max-w-[220px]">{attachedFile.name}</span>
                <button
                  onClick={() => setAttachedFile(null)}
                  className="ml-1 text-slate-400 hover:text-slate-800 font-bold"
                >
                  ✕
                </button>
              </div>
            )}

            <div className="rounded-[26px] p-2 sm:p-2.5 flex items-center gap-2 shadow-xl glass-copilot-light-input border border-white/90">
              <input
                ref={fileInputRef}
                type="file"
                className="hidden"
                accept=".pdf,.png,.jpg,.jpeg,.txt,.csv"
                onChange={(e) => {
                  if (e.target.files?.[0]) setAttachedFile(e.target.files[0])
                }}
              />
              <button
                type="button"
                onClick={() => fileInputRef.current?.click()}
                title="Attach Document or Timetable"
                className="p-2 rounded-full text-slate-500 hover:text-slate-900 hover:bg-slate-100 transition-all flex-shrink-0 text-lg"
              >
                +
              </button>

              <button
                type="button"
                onClick={toggleSpeech}
                title={isListening ? 'Stop listening' : 'Voice Input'}
                className={`p-2 rounded-full transition-all flex-shrink-0 ${
                  isListening
                    ? 'text-amber-600 bg-amber-100 animate-pulse'
                    : 'text-slate-500 hover:text-slate-900 hover:bg-slate-100'
                }`}
              >
                🎙️
              </button>

              <input
                type="text"
                value={input}
                onChange={(e) => setInput(e.target.value)}
                onKeyDown={handleKeyDown}
                placeholder={attachedFile ? 'Add a note with this file...' : 'Message Copilot'}
                className="flex-1 bg-transparent px-2 py-1 text-xs sm:text-sm text-slate-900 placeholder-slate-400 focus:outline-none font-normal"
              />

              <button
                type="button"
                onClick={() => handleSend()}
                disabled={(!input.trim() && !attachedFile) || loading}
                className="px-4 py-2 rounded-full text-xs font-semibold tracking-wide transition-all disabled:opacity-30 flex-shrink-0 flex items-center gap-1 text-white bg-blue-600 hover:bg-blue-700 shadow-md active:scale-95"
              >
                <span>Send</span>
                <span>→</span>
              </button>
            </div>
          </div>

        </div>
      )}

    </div>
  )
}
