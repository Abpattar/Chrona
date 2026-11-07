import { useState, useRef, useEffect, useCallback } from 'react'
import { api } from '../lib/api'
import dayjs from 'dayjs'

const SUGGESTIONS = [
  'I have college from 9 AM to 4 PM every weekday',
  'I need to finish my project by Friday',
  'I have an exam next week — help me prepare',
  'I want to exercise 4 days a week',
  'I missed my study session today — reschedule it',
  'I have a flute class every Sunday at 5 PM',
]

function Message({ msg }) {
  const isUser = msg.role === 'user'

  return (
    <div className={`flex gap-3 ${isUser ? 'flex-row-reverse' : 'flex-row'} animate-fade-in`}>
      {/* Avatar */}
      <div className="w-7 h-7 rounded-full flex items-center justify-center flex-shrink-0 text-xs font-bold"
        style={{
          background: isUser
            ? 'linear-gradient(135deg, #4da3ff, #6366f1)'
            : 'linear-gradient(135deg, #3ddc97, #22c55e)',
        }}>
        {isUser ? 'U' : 'C'}
      </div>

      {/* Bubble */}
      <div className={`max-w-[80%] rounded-2xl px-4 py-3 ${isUser ? 'rounded-tr-sm' : 'rounded-tl-sm'}`}
        style={{
          background: isUser
            ? 'rgba(77,163,255,0.12)'
            : 'rgba(255,255,255,0.04)',
          border: `1px solid ${isUser ? 'rgba(77,163,255,0.2)' : 'rgba(255,255,255,0.06)'}`,
        }}>
        <p className="text-sm leading-relaxed whitespace-pre-wrap" style={{ color: 'var(--ch-text)' }}>
          {msg.content}
        </p>

        {/* Extracted tasks */}
        {msg.tasks_extracted?.length > 0 && (
          <div className="mt-3 pt-3" style={{ borderTop: '1px solid rgba(255,255,255,0.08)' }}>
            <p className="text-[10px] uppercase tracking-wider font-semibold mb-2" style={{ color: 'var(--ch-text-muted)' }}>
              Extracted ({msg.tasks_extracted.length})
            </p>
            <div className="space-y-1">
              {msg.tasks_extracted.map((t, i) => (
                <div key={i} className="flex items-center gap-2 text-xs">
                  <span style={{ color: '#3ddc97' }}>✓</span>
                  <span style={{ color: 'var(--ch-text-dim)' }}>{t.title}</span>
                  {t.task_type && (
                    <span className="ml-auto text-[9px] px-1.5 py-0.5 rounded"
                      style={{ background: 'rgba(77,163,255,0.1)', color: '#4da3ff' }}>
                      {t.task_type}
                    </span>
                  )}
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Clarification */}
        {msg.clarification_needed && (
          <div className="mt-2 p-2 rounded-lg text-xs" style={{ background: 'rgba(255,159,67,0.1)', color: '#ff9f43' }}>
            ❓ {msg.clarification_needed}
          </div>
        )}

        {/* Schedule trigger */}
        {msg.schedule_trigger && (
          <div className="mt-2">
            <button className="text-xs px-3 py-1 rounded-lg font-semibold"
              style={{ background: 'rgba(61,220,151,0.12)', color: '#3ddc97', border: '1px solid rgba(61,220,151,0.2)' }}>
              ✦ Generate Schedule Now
            </button>
          </div>
        )}

        {/* Decomposition */}
        {msg.decomposition && (
          <div className="mt-3 pt-3" style={{ borderTop: '1px solid rgba(255,255,255,0.08)' }}>
            <p className="text-[10px] uppercase tracking-wider font-semibold mb-2" style={{ color: 'var(--ch-text-muted)' }}>
              Project Plan ({msg.decomposition.subtasks?.length || 0} tasks)
            </p>
            <div className="space-y-1">
              {msg.decomposition.subtasks?.slice(0, 5).map((s, i) => (
                <div key={i} className="flex items-center gap-2 text-xs">
                  <span className="w-4 h-4 rounded-full flex items-center justify-center text-[9px] font-bold flex-shrink-0"
                    style={{ background: 'rgba(77,163,255,0.15)', color: '#4da3ff' }}>
                    {s.order || i + 1}
                  </span>
                  <span style={{ color: 'var(--ch-text-dim)' }}>{s.title}</span>
                  <span className="ml-auto text-[9px]" style={{ color: 'var(--ch-text-muted)' }}>
                    {s.duration_minutes}m
                  </span>
                </div>
              ))}
            </div>
          </div>
        )}

        <p className="text-[9px] mt-2 font-mono" style={{ color: 'var(--ch-text-muted)' }}>
          {dayjs(msg.timestamp).format('h:mm A')}
        </p>
      </div>
    </div>
  )
}

export default function Chat() {
  const [messages, setMessages] = useState([
    {
      id: '0',
      role: 'assistant',
      content: "Hi! I'm Chrona, your personal AI scheduling assistant.\n\nTell me about your commitments, goals, or what you need to accomplish. I'll understand your schedule and help you plan your time intelligently.\n\nFor example: \"I have college from 9 AM to 4 PM every weekday, an exam next week, and I want to exercise 4 days a week.\"",
      timestamp: new Date().toISOString(),
    }
  ])
  const [input, setInput] = useState('')
  const [loading, setLoading] = useState(false)
  const [sessionId] = useState(() => `session-${Date.now()}`)
  const bottomRef = useRef(null)
  const textareaRef = useRef(null)

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  const sendMessage = useCallback(async (text) => {
    if (!text.trim() || loading) return

    const userMsg = {
      id: Date.now().toString(),
      role: 'user',
      content: text,
      timestamp: new Date().toISOString(),
    }
    setMessages(prev => [...prev, userMsg])
    setInput('')
    setLoading(true)

    try {
      const result = await api.chat(text, sessionId)

      const assistantMsg = {
        id: (Date.now() + 1).toString(),
        role: 'assistant',
        content: result.message || "I've processed your request.",
        timestamp: new Date().toISOString(),
        tasks_extracted: result.tasks_extracted || [],
        tasks_saved: result.tasks_saved || [],
        clarification_needed: result.clarification_needed,
        schedule_trigger: result.schedule_trigger,
        decomposition: result.decomposition,
      }
      setMessages(prev => [...prev, assistantMsg])
    } catch (e) {
      setMessages(prev => [...prev, {
        id: (Date.now() + 1).toString(),
        role: 'assistant',
        content: `Sorry, I couldn't process that: ${e.message}. Make sure the backend is running.`,
        timestamp: new Date().toISOString(),
        isError: true,
      }])
    } finally {
      setLoading(false)
    }
  }, [loading, sessionId])

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      sendMessage(input)
    }
  }

  return (
    <div className="flex-1 flex flex-col h-full overflow-hidden">
      {/* Messages */}
      <div className="flex-1 overflow-y-auto px-4 py-5 space-y-4">
        {messages.map(msg => (
          <Message key={msg.id} msg={msg} />
        ))}

        {/* Loading indicator */}
        {loading && (
          <div className="flex gap-3 animate-fade-in">
            <div className="w-7 h-7 rounded-full flex items-center justify-center text-xs font-bold"
              style={{ background: 'linear-gradient(135deg, #3ddc97, #22c55e)' }}>
              C
            </div>
            <div className="rounded-2xl rounded-tl-sm px-4 py-3"
              style={{ background: 'rgba(255,255,255,0.04)', border: '1px solid rgba(255,255,255,0.06)' }}>
              <div className="flex gap-1 items-center">
                <div className="w-1.5 h-1.5 rounded-full animate-bounce" style={{ background: '#3ddc97', animationDelay: '0ms' }} />
                <div className="w-1.5 h-1.5 rounded-full animate-bounce" style={{ background: '#3ddc97', animationDelay: '150ms' }} />
                <div className="w-1.5 h-1.5 rounded-full animate-bounce" style={{ background: '#3ddc97', animationDelay: '300ms' }} />
              </div>
            </div>
          </div>
        )}

        <div ref={bottomRef} />
      </div>

      {/* Suggestions */}
      {messages.length <= 1 && (
        <div className="px-4 pb-2 flex gap-2 overflow-x-auto scrollbar-none">
          {SUGGESTIONS.slice(0, 3).map((s, i) => (
            <button key={i} onClick={() => sendMessage(s)}
              className="flex-shrink-0 text-xs px-3 py-1.5 rounded-lg font-medium transition-all hover:-translate-y-0.5"
              style={{
                background: 'rgba(255,255,255,0.04)',
                border: '1px solid rgba(255,255,255,0.08)',
                color: 'var(--ch-text-dim)',
              }}>
              {s}
            </button>
          ))}
        </div>
      )}

      {/* Input */}
      <div className="flex-shrink-0 px-4 pb-4">
        <div className="flex gap-2 items-end glass rounded-2xl p-2"
          style={{ border: '1px solid rgba(77,163,255,0.2)' }}>
          <textarea
            ref={textareaRef}
            value={input}
            onChange={e => setInput(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="Tell me what you need to accomplish..."
            rows={1}
            className="flex-1 bg-transparent resize-none text-sm px-2 py-2 focus:outline-none"
            style={{
              color: 'var(--ch-text)',
              maxHeight: '120px',
              overflowY: 'auto',
            }}
            onInput={(e) => {
              e.target.style.height = 'auto'
              e.target.style.height = `${Math.min(e.target.scrollHeight, 120)}px`
            }}
          />
          <button
            onClick={() => sendMessage(input)}
            disabled={loading || !input.trim()}
            className="w-9 h-9 rounded-xl flex items-center justify-center transition-all disabled:opacity-40 hover:-translate-y-0.5"
            style={{ background: 'linear-gradient(135deg, #4da3ff, #6366f1)' }}
          >
            <svg className="w-4 h-4 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M12 19l9 2-9-18-9 18 9-2zm0 0v-8" />
            </svg>
          </button>
        </div>
        <p className="text-[10px] text-center mt-1.5" style={{ color: 'var(--ch-text-muted)' }}>
          Press Enter to send · Shift+Enter for new line
        </p>
      </div>
    </div>
  )
}

