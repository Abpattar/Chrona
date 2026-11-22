import { useState, useRef, useEffect, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { api } from '../lib/api'
import dayjs from 'dayjs'

const QUICK_PROMPTS = [
  {
    tag: 'FIXED EVENT',
    title: 'Family Meeting',
    desc: 'Tomorrow at 5:00 PM (Immovable commitment)',
    prompt: 'I have a family meeting tomorrow at 5pm',
    accent: '#f8fafc',
  },
  {
    tag: 'ROUTINE',
    title: 'College Timetable',
    desc: 'Weekdays 9 AM – 4 PM + 2h deep focus study block',
    prompt: 'I have college 9 AM to 4 PM every weekday, and need to study for 2 hours daily',
    accent: '#38bdf8',
  },
  {
    tag: 'GOAL PLANNING',
    title: 'Exam Preparation',
    desc: 'Structure daily study blocks leading up to the exam',
    prompt: 'I have an exam next week — help me prepare with scheduled study blocks',
    accent: '#fbbf24',
  },
  {
    tag: 'ADAPTIVE REPAIR',
    title: 'Auto-Reschedule Missed',
    desc: 'Shift missed sessions into the next available focus window',
    prompt: 'I missed my study session today — reschedule it to the next open slot',
    accent: '#c084fc',
  },
]

function TaskCard({ task, onNavigateMap, onNavigateToday }) {
  const startDay = task.start_time || task.fixed_start ? dayjs(task.start_time || task.fixed_start) : null
  const endDay = task.end_time || task.fixed_end ? dayjs(task.end_time || task.fixed_end) : (startDay ? startDay.add(task.duration_minutes || 60, 'minute') : null)
  const isImmovable = task.movability === 'immovable' || task.flexibility === false || task.controllability === 'external'

  return (
    <div
      className="mt-3.5 p-4 rounded-2xl relative overflow-hidden transition-all duration-300"
      style={{
        background: 'linear-gradient(135deg, rgba(16, 22, 36, 0.95), rgba(11, 15, 24, 0.95))',
        border: isImmovable ? '1px solid rgba(248, 250, 252, 0.25)' : '1px solid rgba(56, 189, 248, 0.28)',
        boxShadow: '0 8px 24px rgba(0, 0, 0, 0.5)',
      }}
    >
      <div className="flex items-center justify-between gap-2 mb-2">
        <div className="flex items-center gap-1.5">
          <span
            className="text-[9px] font-semibold uppercase tracking-wider px-2 py-0.5 rounded"
            style={{
              background: isImmovable ? 'rgba(248, 250, 252, 0.08)' : 'rgba(56, 189, 248, 0.1)',
              color: isImmovable ? '#f1f5f9' : '#38bdf8',
              border: `1px solid ${isImmovable ? 'rgba(248, 250, 252, 0.2)' : 'rgba(56, 189, 248, 0.25)'}`,
            }}
          >
            {isImmovable ? 'Immovable' : 'Fluid Focus'}
          </span>
          <span className="text-[10px] font-mono text-emerald-400 font-semibold flex items-center gap-1">
            <span>●</span> Scheduled
          </span>
        </div>

        <span className="text-[10px] font-mono text-slate-400">
          {task.duration_minutes || 60}m duration
        </span>
      </div>

      <h3 className="text-base font-bold tracking-tight text-slate-100 flex items-center gap-2">
        <span>◈</span>
        <span>{task.title}</span>
      </h3>

      {startDay ? (
        <div className="mt-2.5 flex items-center gap-2 flex-wrap text-xs font-mono">
          <div className="px-2.5 py-1 rounded-lg bg-white/[0.04] border border-slate-700/40 text-slate-200 flex items-center gap-1.5 font-medium">
            <span>📅</span>
            <span>{startDay.format('dddd, MMMM D')}</span>
          </div>
          <div className="px-2.5 py-1 rounded-lg bg-white/[0.04] border border-slate-700/40 text-cyan-300 flex items-center gap-1.5 font-medium">
            <span>⏰</span>
            <span>{startDay.format('h:mm A')} → {endDay ? endDay.format('h:mm A') : ''}</span>
          </div>
        </div>
      ) : (
        <div className="mt-2 text-xs font-mono text-cyan-300">
          ⏳ Flexible unassigned focus block — placed in schedule
        </div>
      )}

      {task.transition_reason && (
        <div className="mt-2.5 p-2 rounded-xl bg-amber-500/[0.08] border border-amber-500/20 text-[11px] font-mono text-amber-200">
          ⚡ <span className="font-semibold text-amber-300">Buffer: </span>
          {task.transition_reason}
        </div>
      )}

      <div className="mt-3.5 pt-2.5 border-t border-slate-700/30 flex items-center gap-2 flex-wrap">
        {onNavigateMap && (
          <button
            onClick={onNavigateMap}
            className="px-3 py-1.5 rounded-xl text-xs font-semibold tracking-wider text-slate-200 bg-white/[0.04] hover:bg-white/[0.08] border border-slate-600/30 transition-all flex items-center gap-1.5"
          >
            <span>🗺️</span>
            <span>Mind Map View</span>
          </button>
        )}
        {onNavigateToday && (
          <button
            onClick={onNavigateToday}
            className="px-3 py-1.5 rounded-xl text-xs font-semibold tracking-wider text-cyan-300 bg-cyan-500/10 hover:bg-cyan-500/20 border border-cyan-500/25 transition-all flex items-center gap-1.5"
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

  return (
    <div className={`flex gap-3.5 ${isUser ? 'flex-row-reverse' : 'flex-row'} animate-fade-in`}>
      {/* Avatar */}
      <div
        className="w-8 h-8 rounded-full flex items-center justify-center flex-shrink-0 text-xs font-bold shadow-md"
        style={{
          background: isUser ? '#1e293b' : '#0f172a',
          border: `1px solid ${isUser ? 'rgba(203, 213, 225, 0.3)' : 'rgba(56, 189, 248, 0.4)'}`,
          color: isUser ? '#f1f5f9' : '#38bdf8',
        }}
      >
        {isUser ? 'U' : '✧'}
      </div>

      <div
        className={`max-w-[88%] sm:max-w-[80%] rounded-2xl p-4 shadow-xl relative ${
          isUser
            ? 'rounded-tr-sm bg-[#131926] border border-slate-700/50 text-slate-100'
            : msg.isError
            ? 'rounded-tl-sm bg-red-950/30 border border-red-500/30 text-red-200'
            : 'rounded-tl-sm bg-[#0e121c] border border-slate-700/40 text-slate-200'
        }`}
      >
        {msg.attachedFile && (
          <div className="mb-2.5 inline-flex items-center gap-1.5 px-3 py-1 rounded-xl text-xs font-mono bg-white/[0.04] text-slate-200 border border-slate-700/50">
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

        {msg.quick_actions && msg.quick_actions.length > 0 && (
          <div className="mt-3.5 pt-2.5 flex flex-wrap gap-2 border-t border-slate-700/30">
            {msg.quick_actions.map((qa, i) => (
              <button
                key={i}
                onClick={() => onQuickAction && onQuickAction(qa)}
                className="px-3.5 py-1.5 rounded-xl text-xs font-semibold tracking-wider transition-all border shadow-sm flex items-center gap-1.5 hover:scale-105 active:scale-95"
                style={{
                  background: qa.variant === 'warning' ? 'rgba(245, 158, 11, 0.15)' : 'rgba(56, 189, 248, 0.12)',
                  borderColor: qa.variant === 'warning' ? 'rgba(245, 158, 11, 0.35)' : 'rgba(56, 189, 248, 0.35)',
                  color: qa.variant === 'warning' ? '#fbbf24' : '#7dd3fc',
                }}
              >
                <span>{qa.label}</span>
              </button>
            ))}
          </div>
        )}

        {msg.clarification_needed && (
          <div className="mt-3 p-3 rounded-xl text-xs bg-amber-500/10 border border-amber-500/25 text-amber-300 font-mono">
            ⚠️ <span className="font-bold">Missing Detail:</span> {msg.clarification_needed}
          </div>
        )}

        <div className="text-[10px] font-mono text-slate-500 text-right mt-2">
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
  const [sessionId, setSessionId] = useState(() => localStorage.getItem(CHAT_SESSION_KEY) || `session-${Date.now()}`)

  const bottomRef = useRef(null)
  const fileInputRef = useRef(null)
  const speechRecognitionRef = useRef(null)

  useEffect(() => {
    try {
      localStorage.setItem(CHAT_MESSAGES_KEY, JSON.stringify(messages.slice(-80)))
    } catch {}
  }, [messages])

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages, loading])

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

  const showSuggestions = messages.length <= 1

  return (
    <div
      className="flex-1 flex flex-col h-full overflow-hidden relative"
      onDragEnter={handleDrag}
      onDragOver={handleDrag}
      onDragLeave={handleDrag}
      onDrop={handleDrop}
    >
      {/* File Drop Overlay */}
      {dragActive && (
        <div className="absolute inset-0 z-50 flex items-center justify-center bg-[#07090e]/95 backdrop-blur-md border-2 border-dashed border-cyan-400 pointer-events-none animate-fade-in">
          <div className="flex flex-col items-center gap-3 text-center px-4">
            <div className="w-16 h-16 rounded-2xl bg-cyan-950/60 border border-cyan-400/40 flex items-center justify-center text-3xl shadow-[0_0_20px_rgba(56,189,248,0.25)]">
              📄
            </div>
            <h3 className="text-xl font-bold text-slate-100 tracking-tight">
              Drop Timetable or Document
            </h3>
            <p className="text-xs text-cyan-300 font-mono">PDF, PNG, JPG, or Text supported</p>
          </div>
        </div>
      )}


      {/* Message Stream */}
      <div className="flex-1 min-h-0 overflow-y-auto px-3 sm:px-6 py-3 sm:py-5 max-w-3xl mx-auto w-full space-y-3">
        {messages.map((m) => (
          <MessageBubbleItem
            key={m.id}
            msg={m}
            onNavigateMap={() => navigate('/calendar')}
            onNavigateToday={() => navigate('/dashboard')}
          />
        ))}

        {/* Loading State */}
        {loading && (
          <div className="flex gap-2.5 animate-fade-in">
            <div className="w-7 h-7 rounded-full bg-slate-800 border border-slate-600/30 flex items-center justify-center text-xs text-cyan-300 shadow-sm flex-shrink-0">
              ✧
            </div>
            <div className="rounded-xl rounded-tl-sm px-3.5 py-2.5 bg-[#0e1422] border border-slate-700/40 flex items-center gap-2 shadow-sm">
              <div className="w-1.5 h-1.5 rounded-full bg-cyan-400 animate-ping" />
              <span className="text-[11px] text-slate-300 tracking-wide font-mono">
                Reasoning schedule & transition buffers...
              </span>
            </div>
          </div>
        )}

        {/* Quick Scheduling Suggestions */}
        {showSuggestions && !loading && (
          <div className="mt-4 sm:mt-6 animate-fade-in">
            <div className="text-center mb-3">
              <span className="text-[9px] sm:text-[10px] font-semibold tracking-wider uppercase text-slate-400 bg-white/[0.04] border border-slate-700/40 px-2.5 py-0.5 rounded-full font-mono">
                Quick Scheduling Templates
              </span>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 sm:gap-2.5 max-w-xl mx-auto">
              {QUICK_PROMPTS.map((s, idx) => (
                <button
                  key={idx}
                  onClick={() => handleSend(s.prompt)}
                  className="p-3 rounded-xl bg-[#0e1422]/90 hover:bg-[#151e32] border border-slate-800 hover:border-slate-600 text-left transition-all duration-200 group flex flex-col justify-between shadow-sm relative active:scale-98"
                >
                  <div className="flex items-center justify-between mb-1">
                    <span
                      className="text-[8px] sm:text-[9px] font-bold tracking-wider uppercase px-1.5 py-0.5 rounded"
                      style={{
                        background: 'rgba(255, 255, 255, 0.05)',
                        color: s.accent,
                        border: '1px solid rgba(255, 255, 255, 0.1)',
                      }}
                    >
                      {s.tag}
                    </span>
                    <span className="text-xs text-slate-500 group-hover:text-slate-300 transition-colors">→</span>
                  </div>
                  <h4 className="text-xs sm:text-sm font-bold tracking-tight text-slate-200 group-hover:text-white transition-colors">
                    {s.title}
                  </h4>
                  <p className="text-[10px] sm:text-[11px] text-slate-400 mt-0.5 leading-snug">
                    {s.desc}
                  </p>
                </button>
              ))}
            </div>
          </div>
        )}

        <div ref={bottomRef} />
      </div>

      {/* Floating Bottom Input Dock */}
      <div className="flex-none p-2 sm:p-3 max-w-3xl mx-auto w-full">
        {attachedFile && (
          <div className="mb-1.5 inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-lg bg-white/[0.05] border border-slate-600/40 text-slate-200 text-[11px] font-mono shadow-sm animate-fade-in">
            <span>📄</span>
            <span className="font-semibold truncate max-w-[200px]">{attachedFile.name}</span>
            <button
              onClick={() => setAttachedFile(null)}
              className="ml-1 text-slate-400 hover:text-white font-bold"
            >
              ✕
            </button>
          </div>
        )}

        <div
          className="rounded-xl sm:rounded-2xl p-1.5 sm:p-2 flex items-center gap-1.5 sm:gap-2 shadow-2xl bg-[#0c111e]/95 border border-slate-700/60 backdrop-blur-xl"
        >
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
            className="p-2 rounded-lg text-slate-400 hover:text-slate-200 hover:bg-white/[0.05] transition-all flex-shrink-0"
          >
            📎
          </button>

          <button
            type="button"
            onClick={toggleSpeech}
            title={isListening ? 'Stop listening' : 'Voice Input'}
            className={`p-2 rounded-lg transition-all flex-shrink-0 ${
              isListening ? 'text-amber-400 bg-amber-500/20 animate-pulse' : 'text-slate-400 hover:text-slate-200 hover:bg-white/[0.05]'
            }`}
          >
            🎤
          </button>

          {messages.length > 1 && (
            <button
              type="button"
              onClick={handleClearChat}
              title="Clear Conversation"
              className="p-2 rounded-lg text-slate-400 hover:text-rose-400 hover:bg-rose-500/10 transition-all flex-shrink-0"
            >
              🗑️
            </button>
          )}

          <textarea
            rows={1}
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder={attachedFile ? 'Add a note with this file...' : 'Tell Chrona your tasks (e.g. "Meeting at 5pm")...'}
            className="flex-1 bg-transparent px-1.5 py-1 text-xs sm:text-sm text-slate-200 placeholder-slate-500 focus:outline-none resize-none max-h-24 font-normal"
          />

          <button
            type="button"
            onClick={() => handleSend()}
            disabled={(!input.trim() && !attachedFile) || loading}
            className="px-3 sm:px-4 py-1.5 sm:py-2 rounded-lg sm:rounded-xl text-xs font-semibold tracking-wide transition-all disabled:opacity-30 flex-shrink-0 flex items-center gap-1 text-white bg-gradient-to-r from-cyan-600 to-blue-600 hover:from-cyan-500 hover:to-blue-500 shadow-[0_0_12px_rgba(56,189,248,0.25)] active:scale-95"
          >
            <span>Send</span>
            <span>→</span>
          </button>
        </div>
      </div>

    </div>
  )
}
