/**
 * Time-of-day Dynamic Sky Horizon helper
 * Shifts smoothly reflecting the real sky:
 * - Morning (5 AM - 11 AM): Sunrise golden orange
 * - Afternoon (11 AM - 5 PM): Bright daylight yellow & amber
 * - Evening (5 PM - 8 PM): Golden hour shifting to sunset pink-orange
 * - Night (8 PM - 5 AM): Deep night sky blue & midnight navy
 */
export function getSkyTheme(hour = new Date().getHours()) {
  if (hour >= 5 && hour < 11) {
    return {
      phase: 'morning',
      label: 'Sunrise Orange',
      bgClass: 'bg-sky-morning',
      horizonGlow: 'from-[#f59e0b]/25 via-[#d97706]/15 to-transparent',
      orbLeft: 'bg-orange-500/15',
      orbRight: 'bg-amber-400/18',
      accentColor: '#f59e0b',
    }
  }

  if (hour >= 11 && hour < 17) {
    return {
      phase: 'afternoon',
      label: 'Midday Yellow',
      bgClass: 'bg-sky-afternoon',
      horizonGlow: 'from-[#fbbf24]/28 via-[#f59e0b]/16 to-transparent',
      orbLeft: 'bg-amber-400/16',
      orbRight: 'bg-yellow-300/18',
      accentColor: '#fbbf24',
    }
  }

  if (hour >= 17 && hour < 20) {
    return {
      phase: 'evening',
      label: 'Sunset Pink-Orange',
      bgClass: 'bg-sky-evening',
      horizonGlow: 'from-[#f43f5e]/25 via-[#ea580c]/18 to-transparent',
      orbLeft: 'bg-pink-500/16',
      orbRight: 'bg-orange-500/18',
      accentColor: '#f43f5e',
    }
  }

  return {
    phase: 'night',
    label: 'Midnight Blue',
    bgClass: 'bg-sky-night',
    horizonGlow: 'from-[#38bdf8]/18 via-[#1e3a8a]/20 to-transparent',
    orbLeft: 'bg-blue-600/15',
    orbRight: 'bg-cyan-500/14',
    accentColor: '#38bdf8',
  }
}
