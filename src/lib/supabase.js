import { createClient } from '@supabase/supabase-js'

const supabaseUrl = 'https://tvzenknzcxuegkzujihu.supabase.co'
const supabaseAnonKey = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InR2emVua256Y3h1ZWdrenVqaWh1Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzI1NTYyNDIsImV4cCI6MjA4ODEzMjI0Mn0.rbceObIw6hMdWyCtBEu487cPAo3jxgtyw_3X44vsySE'

export const supabase = createClient(supabaseUrl, supabaseAnonKey)
