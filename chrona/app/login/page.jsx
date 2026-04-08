// ============================================================
// login/page.jsx — Re-imagined login in Analog style
// ============================================================

'use client';

import { useState } from 'react';
import Link from 'next/link';
import PaperGrain from '@/components/PaperGrain';

export default function LoginPage() {
  const [tab, setTab] = useState('signin'); // 'signin' or 'signup'
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');

  return (
    <main className="min-h-screen relative flex items-center justify-center overflow-hidden">
      {/* Texture Layer */}
      <PaperGrain />

      {/* Form Container: Pinned Paper Slip */}
      <div 
        className="relative z-10 w-full max-w-md p-4 animate-in fade-in zoom-in duration-500"
        style={{ transform: 'rotate(-1deg)' }}
      >
        {/* Decorative Push Pin at the top */}
        <div className="absolute -top-4 left-1/2 -translate-x-1/2 z-20 drop-shadow-md">
          <span
            className="material-symbols-outlined text-4xl"
            style={{ color: '#ad170c', fontVariationSettings: "'FILL' 1" }}
          >
            push_pin
          </span>
        </div>

        {/* Main Card */}
        <div className="paper-slip shadow-2xl p-10 flex flex-col items-center relative overflow-hidden bg-white">
          {/* Subtle horizontal lines for the whole card background, but we'll mask important sections */}
          <div className="absolute inset-0 ruled-paper opacity-50 pointer-events-none" />
          
          {/* Logo / Header - Masked */}
          <div className="mb-8 text-center relative z-10 bg-white px-6 py-2 rounded-lg">
            <div 
              className="text-5xl mb-2" 
              style={{ fontFamily: 'Permanent Marker, cursive', color: '#1c1c18' }}
            >
              C
            </div>
            <h1 
              className="text-2xl uppercase tracking-[0.2em] font-bold"
              style={{ fontFamily: 'Space Grotesk, sans-serif' }}
            >
              Welcome to <span style={{ color: '#ad170c' }}>Chrona</span>
            </h1>
            <p 
              className="text-sm italic opacity-60 mt-1"
              style={{ fontFamily: 'Newsreader, serif' }}
            >
              Sign in to your AI Second Brain
            </p>
          </div>

          {/* Tabs: Sign In / Sign Up - Masked */}
          <div className="flex w-full mb-8 border-b border-primary/10 relative z-10 bg-white/60 backdrop-blur-xs">
            <button
              onClick={() => setTab('signin')}
              className={`flex-1 pb-3 text-xs uppercase tracking-widest font-bold transition-all ${
                tab === 'signin' ? 'text-primary' : 'text-black/40'
              }`}
              style={{ fontFamily: 'Space Grotesk, sans-serif' }}
            >
              Sign In
              {tab === 'signin' && (
                <div className="h-0.5 bg-primary mt-3 w-full animate-in slide-in-from-left duration-300" />
              )}
            </button>
            <button
              onClick={() => setTab('signup')}
              className={`flex-1 pb-3 text-xs uppercase tracking-widest font-bold transition-all ${
                tab === 'signup' ? 'text-primary' : 'text-black/40'
              }`}
              style={{ fontFamily: 'Space Grotesk, sans-serif' }}
            >
              Sign Up
              {tab === 'signup' && (
                <div className="h-0.5 bg-primary mt-3 w-full animate-in slide-in-from-right duration-300" />
              )}
            </button>
          </div>

          {/* Form */}
          <div className="w-full relative z-10">
            <form className="w-full space-y-6" onSubmit={(e) => e.preventDefault()}>
            {/* Username Field */}
            <div className="space-y-1">
              <label 
                className="text-[10px] uppercase tracking-widest font-bold opacity-60"
                style={{ fontFamily: 'Work Sans, sans-serif' }}
              >
                Username
              </label>
              <input
                type="text"
                placeholder="Enter username"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                className="w-full bg-white/80 backdrop-blur-sm border-2 border-black/5 px-4 py-3 focus:border-primary outline-none transition-all shadow-sm"
                style={{ 
                  fontFamily: 'Newsreader, serif', 
                  fontSize: '1.1rem',
                }}
              />
            </div>

            {/* Password Field */}
            <div className="space-y-1">
              <label 
                className="text-[10px] uppercase tracking-widest font-bold opacity-60"
                style={{ fontFamily: 'Work Sans, sans-serif' }}
              >
                Password
              </label>
              <input
                type="password"
                placeholder="••••••••"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className="w-full bg-white/80 backdrop-blur-sm border-2 border-black/5 px-4 py-3 focus:border-primary outline-none transition-all shadow-sm"
                style={{ 
                  fontFamily: 'Newsreader, serif', 
                  fontSize: '1.1rem',
                }}
              />
            </div>

            {/* Action Button */}
            <button
              type="submit"
              className="w-full bg-primary hover:bg-primary-container text-white py-4 font-bold uppercase tracking-widest transition-all shadow-lg active:translate-y-0.5 active:shadow-md mt-4"
              style={{ 
                fontFamily: 'Space Grotesk, sans-serif',
                letterSpacing: '0.2em'
              }}
            >
              {tab === 'signin' ? 'Sign In' : 'Join Now'}
            </button>
          </form>
          </div>

          {/* Back to Home Link */}
          <Link 
            href="/"
            className="mt-8 text-xs uppercase tracking-widest font-bold opacity-40 hover:opacity-100 transition-opacity relative z-10"
            style={{ fontFamily: 'Work Sans, sans-serif' }}
          >
            ← Back to Canvas
          </Link>

        </div>
      </div>
    </main>
  );
}
