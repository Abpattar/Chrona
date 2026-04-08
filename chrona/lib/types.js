// ============================================================
// types.js — Category style constants & helpers
// ============================================================

export const CATEGORY_STYLES = {
  HACKATHON: 'hackathon',
  EXAM: 'exam',
  PERSONAL: 'personal',
  OTHER: 'other',
};

/** All available styles a new category can be assigned */
export const ALL_STYLES = ['hackathon', 'exam', 'personal', 'other'];

/** Pick a random style for a new category */
export function randomStyle() {
  return ALL_STYLES[Math.floor(Math.random() * ALL_STYLES.length)];
}

/** Generate a unique ID */
export function generateId() {
  return Math.random().toString(36).substring(2, 10);
}
