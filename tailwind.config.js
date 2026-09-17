/** @type {import('tailwindcss').Config} */
export default {
    content: [
        "./index.html",
        "./src/**/*.{js,ts,jsx,tsx}",
    ],
    theme: {
        extend: {
            fontFamily: {
                sans: ['Space Grotesk', 'Inter', 'system-ui', 'sans-serif'],
                mono: ['JetBrains Mono', 'monospace'],
            },
            colors: {
                nv: {
                    base: '#070d1f',
                    deep: '#0a1128',
                    surface: 'rgba(15, 25, 60, 0.65)',
                    card: 'rgba(20, 30, 70, 0.45)',
                    border: 'rgba(255, 255, 255, 0.08)',
                    'border-bright': 'rgba(255, 255, 255, 0.15)',
                    text: '#e2e8f0',
                    'text-dim': '#94a3b8',
                    'text-muted': '#64748b',
                    accent: '#4da3ff',
                    'accent-soft': 'rgba(77, 163, 255, 0.15)',
                    purple: '#a78bfa',
                    critical: '#ff4d4d',
                    high: '#ff9f43',
                    medium: '#4da3ff',
                    low: '#3ddc97',
                },
            },
            boxShadow: {
                'glass': '0 8px 32px rgba(0, 0, 0, 0.4)',
                'glass-sm': '0 4px 16px rgba(0, 0, 0, 0.3)',
                'glow-blue': '0 0 20px rgba(77, 163, 255, 0.3)',
                'glow-red': '0 0 20px rgba(255, 77, 77, 0.3)',
                'glow-orange': '0 0 20px rgba(255, 159, 67, 0.3)',
                'glow-green': '0 0 20px rgba(61, 220, 151, 0.3)',
                'glow-purple': '0 0 20px rgba(167, 139, 250, 0.3)',
                'inner-glow': 'inset 0 1px 0 rgba(255, 255, 255, 0.05)',
            },
            backdropBlur: {
                'glass': '12px',
            },
            animation: {
                'pulse-slow': 'pulse 3s cubic-bezier(0.4, 0, 0.6, 1) infinite',
                'float': 'float 6s ease-in-out infinite',
                'slide-up': 'slideUp 0.5s ease-out',
                'fade-in': 'fadeIn 0.4s ease-out',
                'glow-pulse': 'glowPulse 2s ease-in-out infinite',
            },
            keyframes: {
                float: {
                    '0%, 100%': { transform: 'translateY(0)' },
                    '50%': { transform: 'translateY(-10px)' },
                },
                slideUp: {
                    '0%': { opacity: '0', transform: 'translateY(20px)' },
                    '100%': { opacity: '1', transform: 'translateY(0)' },
                },
                fadeIn: {
                    '0%': { opacity: '0' },
                    '100%': { opacity: '1' },
                },
                glowPulse: {
                    '0%, 100%': { opacity: '0.6' },
                    '50%': { opacity: '1' },
                },
            },
        },
        fontSize: {
            'fluid-xs': 'clamp(0.6rem, 1vw + 0.5rem, 0.75rem)',
            'fluid-sm': 'clamp(0.75rem, 1.5vw + 0.5rem, 0.875rem)',
            'fluid-base': 'clamp(0.875rem, 2vw + 0.5rem, 1rem)',
            'fluid-lg': 'clamp(1rem, 2.5vw + 0.5rem, 1.125rem)',
            'fluid-xl': 'clamp(1.125rem, 3vw + 0.5rem, 1.25rem)',
            'fluid-2xl': 'clamp(1.25rem, 4vw + 0.5rem, 1.5rem)',
            'fluid-3xl': 'clamp(1.5rem, 5vw + 0.5rem, 1.875rem)',
            'fluid-4xl': 'clamp(1.875rem, 6vw + 0.5rem, 2.25rem)',
            'fluid-5xl': 'clamp(2.25rem, 8vw + 0.5rem, 3rem)',
            'fluid-6xl': 'clamp(2.5rem, 10vw + 0.5rem, 3.75rem)',
        },
    },
    plugins: [],
}
