// ============================================================
// data.js — Seed data matching the Stitch reference exactly
// ============================================================

// ---- Categories (positioned around the "ME" center) --------
export const defaultCategories = [
  {
    id: 'hackathon',
    name: 'HACKATHON',
    style: 'hackathon',
    position: { x: 50, y: 30 },
    rotation: -2,
  },
  {
    id: 'exam',
    name: 'EXAM',
    style: 'exam',
    position: { x: 72, y: 50 },
    rotation: 3,
  },
  {
    id: 'personal',
    name: 'PERSONAL',
    style: 'personal',
    position: { x: 28, y: 50 },
    rotation: -1,
  },
  {
    id: 'other',
    name: 'OTHER',
    style: 'other',
    position: { x: 55, y: 72 },
    rotation: 2,
  },
];

// ---- Tasks (positioned around their parent category) --------
export const defaultTasks = [
  // Hackathon tasks
  {
    id: 'hackzion',
    categoryId: 'hackathon',
    title: 'HACKZION HACKATHON',
    date: 'Apr 9, 12:00AM',
    position: { x: 30, y: 12 },
    rotation: -3,
  },
  {
    id: 'nexify',
    categoryId: 'hackathon',
    title: "NEXIFY '26",
    date: 'Apr 17, 12:00AM',
    position: { x: 72, y: 12 },
    rotation: 2,
  },

  // Exam tasks
  {
    id: 'math-exam-1',
    categoryId: 'exam',
    title: 'MATH EXAM',
    date: 'Apr 9, 4:00PM',
    position: { x: 88, y: 22 },
    rotation: -2,
  },
  {
    id: 'math-exam-2',
    categoryId: 'exam',
    title: 'MATH EXAM',
    date: 'Mar 22, 9:00AM',
    position: { x: 88, y: 65 },
    rotation: 1,
  },

  // Personal tasks
  {
    id: '5km-running',
    categoryId: 'personal',
    title: '5KM RUNNING',
    date: 'Mar 22, 5:00AM',
    position: { x: 8, y: 25 },
    rotation: -6,
  },
  {
    id: 'acharya-fest',
    categoryId: 'personal',
    title: 'ACHARYA FEST',
    date: 'TBD',
    position: { x: 8, y: 75 },
    rotation: 4,
  },

  // Other tasks
  {
    id: 'test-probe',
    categoryId: 'other',
    title: 'TEST_PROBE',
    date: 'TBD',
    position: { x: 30, y: 90 },
    rotation: -1,
  },
  {
    id: 'vinciapest',
    categoryId: 'other',
    title: 'VINCIAPEST',
    date: 'Apr 10, 12:00AM',
    position: { x: 72, y: 90 },
    rotation: 3,
  },
];
