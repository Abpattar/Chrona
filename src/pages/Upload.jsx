import { useState, useRef } from 'react'

export default function Upload() {
    const [text, setText] = useState('')
    const [file, setFile] = useState(null)
    const [status, setStatus] = useState(null)
    const [loading, setLoading] = useState(false)
    const [dragActive, setDragActive] = useState(false)
    const fileInputRef = useRef(null)

    const handleSubmit = async (e) => {
        e.preventDefault()
        if (!text.trim() && !file) {
            setStatus({ type: 'error', message: 'Please enter text or upload a file.' })
            return
        }

        setLoading(true)
        setStatus(null)

        try {
            const formData = new FormData()
            if (file) formData.append('file', file)
            if (text.trim()) formData.append('text', text.trim())

            const res = await fetch('/api/process-event', {
                method: 'POST',
                body: formData,
            })

            if (!res.ok) throw new Error(`Server error: ${res.status}`)
            setStatus({ type: 'success', message: 'Event processed successfully ✨' })
            setText('')
            setFile(null)
            if (fileInputRef.current) fileInputRef.current.value = ''
        } catch (err) {
            setStatus({ type: 'error', message: err.message || 'Failed to process event.' })
        } finally {
            setLoading(false)
        }
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
        if (e.dataTransfer.files?.[0]) setFile(e.dataTransfer.files[0])
    }

    return (
        <div className="flex-1 overflow-y-auto overflow-x-hidden w-full px-4 py-8 sm:px-8 sm:py-12 flex flex-col items-center">
            <div className="w-full max-w-lg animate-fade-in">

                {/* Glass Card */}
                <div className="relative glass rounded-2xl p-5 sm:p-8 shadow-glass" style={{ borderColor: 'rgba(77, 163, 255, 0.12)' }}>
                    {/* Accent glow line */}
                    <div className="absolute top-0 left-4 right-4 h-px" style={{ background: 'linear-gradient(90deg, transparent, #4da3ff, transparent)' }} />

                    {/* Header */}
                    <div className="mb-6 sm:mb-8">
                        <div className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[10px] sm:text-xs font-medium text-nv-accent uppercase tracking-wider mb-4"
                            style={{ background: 'rgba(77, 163, 255, 0.1)', border: '1px solid rgba(77, 163, 255, 0.2)' }}>
                            <div className="w-1.5 h-1.5 rounded-full bg-nv-accent animate-pulse" />
                            System Upload
                        </div>
                        <h1 className="text-2xl sm:text-4xl font-bold text-nv-text tracking-tight leading-none">
                            Add Event
                        </h1>
                        <p className="text-nv-text-dim text-sm mt-3 leading-relaxed">
                            Paste text, drop a PDF, or upload an image to extract event data.
                        </p>
                    </div>

                    <form onSubmit={handleSubmit} className="space-y-5 sm:space-y-6">
                        {/* Text input */}
                        <div>
                            <label htmlFor="event-text" className="block text-xs font-medium text-nv-text-dim uppercase tracking-wider mb-2">
                                Description
                            </label>
                            <textarea
                                id="event-text"
                                rows={4}
                                value={text}
                                onChange={(e) => setText(e.target.value)}
                                placeholder='e.g. "Math exam tomorrow 4 PM"'
                                className="w-full rounded-xl px-4 py-3 text-sm text-nv-text placeholder-nv-text-muted focus:outline-none resize-none font-mono transition-all duration-200"
                                style={{
                                    background: 'rgba(10, 17, 40, 0.8)',
                                    border: '1px solid rgba(255, 255, 255, 0.08)',
                                    boxShadow: 'inset 0 2px 4px rgba(0, 0, 0, 0.3)',
                                }}
                                onFocus={(e) => {
                                    e.target.style.borderColor = 'rgba(77, 163, 255, 0.4)'
                                    e.target.style.boxShadow = 'inset 0 2px 4px rgba(0,0,0,0.3), 0 0 16px rgba(77, 163, 255, 0.15)'
                                }}
                                onBlur={(e) => {
                                    e.target.style.borderColor = 'rgba(255, 255, 255, 0.08)'
                                    e.target.style.boxShadow = 'inset 0 2px 4px rgba(0, 0, 0, 0.3)'
                                }}
                            />
                        </div>

                        {/* File upload */}
                        <div>
                            <label className="block text-xs font-medium text-nv-text-dim uppercase tracking-wider mb-2">
                                File Upload
                            </label>
                            <div
                                onDragEnter={handleDrag}
                                onDragLeave={handleDrag}
                                onDragOver={handleDrag}
                                onDrop={handleDrop}
                                onClick={() => fileInputRef.current?.click()}
                                className="relative rounded-xl p-5 sm:p-8 text-center cursor-pointer transition-all duration-200"
                                style={{
                                    border: dragActive
                                        ? '2px solid #4da3ff'
                                        : '2px dashed rgba(255, 255, 255, 0.1)',
                                    background: dragActive
                                        ? 'rgba(77, 163, 255, 0.08)'
                                        : 'rgba(10, 17, 40, 0.5)',
                                    boxShadow: dragActive ? '0 0 24px rgba(77, 163, 255, 0.15)' : 'none',
                                }}
                            >
                                <input
                                    ref={fileInputRef}
                                    type="file"
                                    accept=".pdf,.png,.jpg,.jpeg,.webp"
                                    onChange={(e) => setFile(e.target.files?.[0] || null)}
                                    className="hidden"
                                />

                                {file ? (
                                    <div className="flex items-center justify-between glass rounded-lg p-3">
                                        <div className="flex items-center gap-3 text-left min-w-0">
                                            <div className="w-10 h-10 flex-shrink-0 rounded-lg flex items-center justify-center text-lg"
                                                style={{ background: 'rgba(77, 163, 255, 0.15)' }}>
                                                📄
                                            </div>
                                            <div className="min-w-0">
                                                <p className="text-sm font-medium text-nv-text truncate max-w-[160px] sm:max-w-[220px]">{file.name}</p>
                                                <p className="text-xs text-nv-text-muted font-mono">{(file.size / 1024).toFixed(1)} KB</p>
                                            </div>
                                        </div>
                                        <button
                                            type="button"
                                            onClick={(e) => {
                                                e.stopPropagation()
                                                setFile(null)
                                                if (fileInputRef.current) fileInputRef.current.value = ''
                                            }}
                                            className="w-8 h-8 ml-2 flex-shrink-0 flex items-center justify-center rounded-lg text-sm font-bold transition-all hover:scale-110"
                                            style={{ background: 'rgba(255, 77, 77, 0.2)', color: '#ff4d4d' }}
                                        >
                                            ✕
                                        </button>
                                    </div>
                                ) : (
                                    <div className="space-y-3">
                                        <div className="w-12 h-12 mx-auto rounded-xl flex items-center justify-center" style={{ background: 'rgba(77, 163, 255, 0.1)' }}>
                                            <svg className="w-6 h-6 text-nv-accent" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
                                                <path strokeLinecap="round" strokeLinejoin="round" d="M3 16.5v2.25A2.25 2.25 0 005.25 21h13.5A2.25 2.25 0 0021 18.75V16.5M12 3v13.5m0-13.5l-4.5 4.5m4.5-4.5l4.5 4.5" />
                                            </svg>
                                        </div>
                                        <p className="text-sm text-nv-text-dim">
                                            Drop <span className="text-nv-accent font-medium">PDF</span> or <span className="text-nv-accent font-medium">Image</span> here
                                        </p>
                                        <p className="text-xs text-nv-text-muted">or click to browse</p>
                                    </div>
                                )}
                            </div>
                        </div>

                        {/* Submit */}
                        <button
                            type="submit"
                            disabled={loading}
                            className="w-full py-3.5 sm:py-4 rounded-xl font-semibold text-white text-sm sm:text-base tracking-wide transition-all duration-200 hover:-translate-y-0.5 active:translate-y-0 disabled:opacity-50 disabled:cursor-not-allowed disabled:transform-none"
                            style={{
                                background: 'linear-gradient(135deg, #4da3ff, #6366f1)',
                                boxShadow: '0 4px 20px rgba(77, 163, 255, 0.3)',
                            }}
                        >
                            {loading ? (
                                <span className="flex items-center justify-center gap-2">
                                    <div className="w-4 h-4 rounded-full border-2 border-white/30 border-t-white animate-spin" />
                                    Processing...
                                </span>
                            ) : 'Process Event'}
                        </button>

                        {/* Status Message */}
                        {status && (
                            <div className={`p-3.5 rounded-xl text-sm font-medium text-center animate-slide-up ${status.type === 'success'
                                ? 'text-nv-low'
                                : 'text-nv-critical'
                                }`}
                                style={{
                                    background: status.type === 'success'
                                        ? 'rgba(61, 220, 151, 0.1)'
                                        : 'rgba(255, 77, 77, 0.1)',
                                    border: `1px solid ${status.type === 'success'
                                        ? 'rgba(61, 220, 151, 0.3)'
                                        : 'rgba(255, 77, 77, 0.3)'}`,
                                }}>
                                {status.message}
                            </div>
                        )}
                    </form>
                </div>
            </div>
        </div>
    )
}
