import { useState } from 'react'
import { api } from '../lib/api'

const ARCHETYPES = [
  { key: 'morning', label: 'Early Bird', desc: 'Most productive before noon', emoji: '🌅' },
  { key: 'afternoon', label: 'Afternoon Focus', desc: 'Peak energy after lunch', emoji: '☀️' },
  { key: 'evening', label: 'Evening Worker', desc: 'Hit stride in the evening', emoji: '🌆' },
  { key: 'night', label: 'Night Owl', desc: 'Thrive late at night', emoji: '🦉' },
]

const WORK_TYPES = [
  { key: 'student', label: 'Student', emoji: '📚' },
  { key: 'professional', label: 'Professional', emoji: '💼' },
  { key: 'freelancer', label: 'Freelancer', emoji: '🎯' },
  { key: 'other', label: 'Other', emoji: '✨' },
]

export default function Onboarding({ onComplete }) {
  const [step, setStep] = useState(0)
  const [form, setForm] = useState({
    productive_time: '',
    work_type: '',
    task_preference: 'flexible',
    study_hours: 4,
    wake_time: '07:00',
    sleep_time: '23:00',
    timezone: Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC',
  })
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)

  const handleSubmit = async () => {
    setLoading(true)
    setError(null)
    try {
      await api.onboarding(form)
      localStorage.setItem('chrona_onboarded', 'true')
      onComplete?.()
    } catch (e) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }

  const steps = [
    // Step 0: Productive time
    {
      title: 'When are you most productive?',
      subtitle: "Chrona will schedule your important tasks during your peak hours.",
      content: (
        <div className="grid grid-cols-2 gap-3">
          {ARCHETYPES.map(a => (
            <button key={a.key}
              onClick={() => { setForm(f => ({ ...f, productive_time: a.key })); setStep(1) }}
              className="p-4 rounded-xl text-left transition-all hover:-translate-y-1"
              style={{
                background: form.productive_time === a.key ? 'rgba(77,163,255,0.12)' : 'rgba(255,255,255,0.03)',
                border: `1px solid ${form.productive_time === a.key ? 'rgba(77,163,255,0.4)' : 'rgba(255,255,255,0.08)'}`,
              }}>
              <span className="text-2xl block mb-2">{a.emoji}</span>
              <p className="font-semibold text-sm" style={{ color: 'var(--ch-text)' }}>{a.label}</p>
              <p className="text-xs mt-0.5" style={{ color: 'var(--ch-text-dim)' }}>{a.desc}</p>
            </button>
          ))}
        </div>
      )
    },
    // Step 1: Work type
    {
      title: 'What best describes you?',
      subtitle: "This helps Chrona understand your typical schedule patterns.",
      content: (
        <div className="grid grid-cols-2 gap-3">
          {WORK_TYPES.map(w => (
            <button key={w.key}
              onClick={() => { setForm(f => ({ ...f, work_type: w.key })); setStep(2) }}
              className="p-4 rounded-xl flex items-center gap-3 transition-all hover:-translate-y-0.5"
              style={{
                background: form.work_type === w.key ? 'rgba(77,163,255,0.12)' : 'rgba(255,255,255,0.03)',
                border: `1px solid ${form.work_type === w.key ? 'rgba(77,163,255,0.4)' : 'rgba(255,255,255,0.08)'}`,
              }}>
              <span className="text-2xl">{w.emoji}</span>
              <span className="font-semibold text-sm" style={{ color: 'var(--ch-text)' }}>{w.label}</span>
            </button>
          ))}
        </div>
      )
    },
    // Step 2: Hours and times
    {
      title: 'Set your daily schedule',
      subtitle: "Chrona won't schedule tasks outside these hours.",
      content: (
        <div className="space-y-5">
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="text-xs uppercase tracking-wider font-semibold block mb-2" style={{ color: 'var(--ch-text-muted)' }}>
                Wake up
              </label>
              <input type="time" value={form.wake_time}
                onChange={e => setForm(f => ({ ...f, wake_time: e.target.value }))}
                className="w-full rounded-xl px-4 py-3 text-sm font-mono focus:outline-none"
                style={{
                  background: 'rgba(10,17,40,0.8)',
                  border: '1px solid rgba(255,255,255,0.1)',
                  color: 'var(--ch-text)',
                }}
              />
            </div>
            <div>
              <label className="text-xs uppercase tracking-wider font-semibold block mb-2" style={{ color: 'var(--ch-text-muted)' }}>
                Sleep
              </label>
              <input type="time" value={form.sleep_time}
                onChange={e => setForm(f => ({ ...f, sleep_time: e.target.value }))}
                className="w-full rounded-xl px-4 py-3 text-sm font-mono focus:outline-none"
                style={{
                  background: 'rgba(10,17,40,0.8)',
                  border: '1px solid rgba(255,255,255,0.1)',
                  color: 'var(--ch-text)',
                }}
              />
            </div>
          </div>

          <div>
            <label className="text-xs uppercase tracking-wider font-semibold block mb-2" style={{ color: 'var(--ch-text-muted)' }}>
              Daily focus hours: {form.study_hours}h
            </label>
            <input type="range" min={1} max={12} value={form.study_hours}
              onChange={e => setForm(f => ({ ...f, study_hours: parseInt(e.target.value) }))}
              className="w-full accent-blue-400"
            />
            <div className="flex justify-between text-[10px] mt-1" style={{ color: 'var(--ch-text-muted)' }}>
              <span>1h</span><span>12h</span>
            </div>
          </div>

          {error && (
            <p className="text-xs text-red-400 p-2 rounded-lg" style={{ background: 'rgba(255,77,77,0.08)' }}>
              {error}
            </p>
          )}

          <button onClick={handleSubmit} disabled={loading}
            className="w-full py-3.5 rounded-xl font-bold text-sm transition-all hover:-translate-y-0.5 disabled:opacity-50"
            style={{ background: 'linear-gradient(135deg, #4da3ff, #6366f1)', color: 'white' }}>
            {loading ? 'Setting up Chrona...' : 'Start Planning →'}
          </button>
        </div>
      )
    }
  ]

  const current = steps[step]

  return (
    <div className="flex-1 flex items-center justify-center px-4 py-8">
      <div className="w-full max-w-sm animate-fade-in">
        {/* Logo */}
        <div className="text-center mb-8">
          <div className="w-14 h-14 mx-auto rounded-2xl flex items-center justify-center text-2xl font-bold text-white mb-4"
            style={{ background: 'linear-gradient(135deg, #4da3ff, #6366f1)', boxShadow: '0 0 32px rgba(77,163,255,0.3)' }}>
            C
          </div>
          <h1 className="text-2xl font-bold tracking-tight" style={{ color: 'var(--ch-text)' }}>
            Welcome to Chrona
          </h1>
          <p className="text-sm mt-1" style={{ color: 'var(--ch-text-dim)' }}>
            Your AI-powered time management assistant
          </p>
        </div>

        {/* Progress */}
        <div className="flex gap-1.5 mb-8">
          {steps.map((_, i) => (
            <div key={i} className="h-1 flex-1 rounded-full transition-all duration-300"
              style={{ background: i <= step ? '#4da3ff' : 'rgba(255,255,255,0.08)' }} />
          ))}
        </div>

        {/* Step */}
        <div className="glass rounded-2xl p-6">
          <h2 className="text-lg font-bold mb-1" style={{ color: 'var(--ch-text)' }}>{current.title}</h2>
          <p className="text-sm mb-5" style={{ color: 'var(--ch-text-dim)' }}>{current.subtitle}</p>
          {current.content}

          {step > 0 && step < 2 && (
            <button onClick={() => setStep(s => s - 1)}
              className="w-full mt-3 py-2 text-xs font-semibold rounded-xl transition-all"
              style={{ background: 'rgba(255,255,255,0.03)', color: 'var(--ch-text-dim)' }}>
              ← Back
            </button>
          )}
        </div>
      </div>
    </div>
  )
}

