import { useState } from 'react'

const QUESTIONS = [
    {
        id: 'productive_time',
        question: 'When are you most productive?',
        subtitle: 'Chrona adapts your schedule to your natural rhythm.',
        options: [
            { label: 'Morning (6am–12pm)', value: 'morning', icon: '🌅' },
            { label: 'Afternoon (12pm–5pm)', value: 'afternoon', icon: '☀️' },
            { label: 'Evening (5pm–9pm)', value: 'evening', icon: '🌆' },
            { label: 'Night (9pm–2am)', value: 'night', icon: '🌙' },
        ]
    },
    {
        id: 'work_type',
        question: 'What best describes you?',
        subtitle: 'This helps us tailor task recommendations.',
        options: [
            { label: 'Student', value: 'student', icon: '📚' },
            { label: 'Developer', value: 'developer', icon: '💻' },
            { label: 'Manager', value: 'manager', icon: '📊' },
            { label: 'Freelancer', value: 'freelancer', icon: '🎨' },
        ]
    },
    {
        id: 'task_preference',
        question: 'How do you prefer to work?',
        subtitle: 'We\'ll optimize block durations accordingly.',
        options: [
            { label: 'Long focus blocks (90+ min)', value: 'long_blocks', icon: '🎯' },
            { label: 'Short bursts (25–45 min)', value: 'short_bursts', icon: '⚡' },
            { label: 'Mixed — depends on task', value: 'mixed', icon: '🔄' },
        ]
    },
    {
        id: 'study_hours',
        question: 'Daily productive hours available?',
        subtitle: 'Be realistic — Chrona learns and adjusts.',
        options: [
            { label: '2 hours', value: 2, icon: '🕐' },
            { label: '4 hours', value: 4, icon: '🕓' },
            { label: '6 hours', value: 6, icon: '🕕' },
            { label: '8+ hours', value: 8, icon: '🕗' },
        ]
    },
    {
        id: 'deadline_style',
        question: 'Your deadline approach?',
        subtitle: 'Last question — we\'ll build your profile.',
        options: [
            { label: 'I plan ahead', value: 'planner', icon: '📅' },
            { label: 'I work under pressure', value: 'pressure', icon: '🔥' },
        ]
    },
]

function deriveArchetype(answers) {
    const time = answers.productive_time
    const type = answers.work_type
    if (type === 'developer') return 'developer'
    if (type === 'manager') return 'manager'
    if (type === 'freelancer') return 'freelancer'
    if (time === 'morning') return 'student_morning'
    if (time === 'night') return 'student_night'
    return 'student_balanced'
}

export default function BehaviorOnboarding({ onComplete }) {
    const [step, setStep] = useState(0)
    const [answers, setAnswers] = useState({})
    const [loading, setLoading] = useState(false)
    const [error, setError] = useState(null)

    const current = QUESTIONS[step]
    const isLast = step === QUESTIONS.length - 1

    const handleSelect = async (value) => {
        const updated = { ...answers, [current.id]: value }
        setAnswers(updated)

        if (isLast) {
            setLoading(true)
            setError(null)
            const archetype = deriveArchetype(updated)

            try {
                const res = await fetch('/api/onboarding', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ ...updated, archetype }),
                })
                if (!res.ok) throw new Error('Onboarding failed')
                const data = await res.json()
                localStorage.setItem('onboarding_complete', 'true')
                localStorage.setItem('chrona_archetype', archetype)
                if (onComplete) onComplete(data.profile)
            } catch (e) {
                setError(e.message)
                localStorage.setItem('onboarding_complete', 'true')
                if (onComplete) onComplete(null)
            } finally {
                setLoading(false)
            }
        } else {
            setStep(step + 1)
        }
    }

    if (loading) {
        return (
            <div className="fixed inset-0 z-50 flex items-center justify-center"
                 style={{ background: 'rgba(15, 13, 11, 0.97)' }}>
                <div className="text-center">
                    <div className="w-12 h-12 border-4 border-ch-peach-soft border-t-ch-peach rounded-full animate-spin mx-auto mb-4" />
                    <p className="text-lg font-semibold" style={{ color: 'var(--ch-text)' }}>Building your profile...</p>
                    <p className="text-sm mt-1" style={{ color: 'var(--ch-text-muted)' }}>This takes just a moment</p>
                </div>
            </div>
        )
    }

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4"
             style={{ background: 'rgba(15, 13, 11, 0.97)' }}>
            <div className="w-full max-w-md animate-fade-in">
                {/* Progress bar */}
                <div className="flex gap-1.5 mb-8">
                    {QUESTIONS.map((_, i) => (
                        <div key={i} className="flex-1 h-1.5 rounded-full transition-all duration-500"
                             style={{
                                 background: i <= step
                                     ? 'linear-gradient(90deg, #E8927C, #D47560)'
                                     : 'rgba(180,140,110,0.15)'
                             }} />
                    ))}
                </div>

                {/* Step counter */}
                <div className="flex items-center gap-2 mb-4">
                    <span className="text-[10px] font-bold uppercase tracking-widest px-2.5 py-1 rounded-full"
                        style={{ background: 'rgba(232,146,124,0.1)', color: 'var(--ch-peach)' }}>
                        Step {step + 1} of {QUESTIONS.length}
                    </span>
                </div>

                {/* Question */}
                <h2 className="text-2xl sm:text-3xl font-bold mb-1.5" style={{ color: 'var(--ch-text)' }}>
                    {current.question}
                </h2>
                <p className="text-sm mb-8" style={{ color: 'var(--ch-text-dim)' }}>
                    {current.subtitle}
                </p>

                {/* Options */}
                <div className="space-y-2.5">
                    {current.options.map((opt) => (
                        <button
                            key={opt.value}
                            onClick={() => handleSelect(opt.value)}
                            className="w-full flex items-center gap-3.5 p-4 rounded-xl text-left transition-all duration-200 hover:scale-[1.01] hover:shadow-warm group"
                            style={{
                                background: 'var(--ch-surface)',
                                border: '1px solid rgba(180,140,110,0.12)',
                                boxShadow: '0 1px 3px rgba(180,140,110,0.06)',
                            }}
                        >
                            <span className="text-2xl group-hover:scale-110 transition-transform duration-200">{opt.icon}</span>
                            <span className="font-semibold" style={{ color: 'var(--ch-text)' }}>{opt.label}</span>
                            <svg className="w-4 h-4 ml-auto opacity-0 group-hover:opacity-100 transition-opacity" style={{ color: 'var(--ch-peach)' }} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                                <path strokeLinecap="round" strokeLinejoin="round" d="M9 5l7 7-7 7" />
                            </svg>
                        </button>
                    ))}
                </div>

                {/* Back button */}
                {step > 0 && (
                    <button
                        onClick={() => setStep(step - 1)}
                        className="mt-5 text-sm font-medium transition-colors flex items-center gap-1"
                        style={{ color: 'var(--ch-text-dim)' }}
                    >
                        ← Back
                    </button>
                )}

                {error && (
                    <p className="mt-4 text-sm font-medium" style={{ color: 'var(--ch-critical)' }}>{error}</p>
                )}
            </div>
        </div>
    )
}
