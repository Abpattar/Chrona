import { createClient } from '@supabase/supabase-js'

// Use the same Supabase project as the backend
// (read from env, falls back to the project configured in .env)
const supabaseUrl = import.meta.env.VITE_SUPABASE_URL || 'https://anpyvughfvfwgklyjbmu.supabase.co'
const supabaseAnonKey = import.meta.env.VITE_SUPABASE_ANON_KEY ||
  'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImFucHl2dWdoZnZmd2drbHlqYm11Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTIyMjAyNzksImV4cCI6MjEwNzc5NjI3OX0.3f_vb1PHYcL9-oUWm6fdTQXiGoCcAO7miYSjeyNj3Fo'

export const supabase = createClient(supabaseUrl, supabaseAnonKey)
