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
      horizonGlow: 'from-[#f59e0b]/40 via-[#d97706]/25 to-transparent',
      orbLeft: 'bg-orange-500/22',
      orbRight: 'bg-amber-400/28',
      accentColor: '#f59e0b',
    }
  }

  if (hour >= 11 && hour < 17) {
    return {
      phase: 'afternoon',
      label: 'Midday Yellow',
      bgClass: 'bg-sky-afternoon',
      horizonGlow: 'from-[#fbbf24]/45 via-[#f59e0b]/28 to-transparent',
      orbLeft: 'bg-amber-400/25',
      orbRight: 'bg-yellow-300/30',
      accentColor: '#fbbf24',
    }
  }

  if (hour >= 17 && hour < 20) {
    return {
      phase: 'evening',
      label: 'Sunset Pink-Orange',
      bgClass: 'bg-sky-evening',
      horizonGlow: 'from-[#f43f5e]/45 via-[#ea580c]/32 to-transparent',
      orbLeft: 'bg-pink-500/28',
      orbRight: 'bg-orange-500/32',
      accentColor: '#f43f5e',
    }
  }

  return {
    phase: 'night',
    label: 'Midnight Blue',
    bgClass: 'bg-sky-night',
    horizonGlow: 'from-[#38bdf8]/25 via-[#1e3a8a]/35 to-transparent',
    orbLeft: 'bg-blue-600/22',
    orbRight: 'bg-cyan-500/20',
    accentColor: '#38bdf8',
  }
}
