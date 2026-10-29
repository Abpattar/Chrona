# Blockers

**Branch:** `chrona-saner-parity`
**Updated:** 2026-10-28

---

## Missing Credentials / Services

| Item | Required For | Status | Notes |
|------|--------------|--------|-------|
| `GEMINI_API_KEY` | Parser, enrichment, explanation agents | ❌ Missing | Add to `.env` |
| `SUPABASE_URL` | All backend DB operations | ❌ Missing | Add to `.env` |
| `SUPABASE_ANON_KEY` | Frontend + backend reads | ❌ Missing | Add to `.env` |
| `SUPABASE_SERVICE_KEY` | Server-side writes (bypass RLS) | ❌ Missing | Add to `.env` |
| `UPSTASH_REDIS_URL` | Behavior profile caching | ❌ Missing | Free tier at upstash.com |
| `UPSTASH_REDIS_TOKEN` | Behavior profile caching | ❌ Missing | Free tier at upstash.com |

---

## Database / Infrastructure

| Item | Required For | Status | Notes |
|------|--------------|--------|-------|
| Supabase project with migrations applied | All Chrona tables (`schedule_items`, `behavior_*`, `repair_proposals`, `onboarding_responses`) | ❌ Not verified | Run `001_create_drafts_table.sql`, `002_archive_trigger.sql`, `003_chrona_schema.sql` in Supabase SQL Editor |
| RLS policies on all user-scoped tables | Security (per-user data isolation) | ❌ Not enabled | Migration 003 comments: "Enable RLS policies as needed" |
| Supabase Realtime on `schedule_items` | `CalendarView.jsx` live updates | ❌ Not enabled | Migration 003: `ALTER PUBLICATION supabase_realtime ADD TABLE schedule_items;` |

---

## Deployment

| Item | Required For | Status | Notes |
|------|--------------|--------|-------|
| Vercel project linked | Preview/production deployment | ❌ Not verified | `vercel link` or dashboard |
| Vercel Python runtime support | Hosting FastAPI backend on Vercel | 🔍 Not checked | **Gate for Phase D** — check current Vercel docs for Python/FastAPI function support, size limits, timeouts |
| Vercel environment variables set | Production secrets | ❌ Not set | `GEMINI_API_KEY`, `SUPABASE_*`, `UPSTASH_*` |

---

## External Dependencies (Human Required)

| Item | Required For | Status | Notes |
|------|--------------|--------|-------|
| Authorized Saner.AI account | `docs/SANER_OBSERVATIONS.md` (observable behaviors) | ❌ Not available | Cannot log in; no scraping/bypass |
| Google Calendar OAuth credentials | Feature #11 (Google Calendar sync) | ❌ Not provided | Out of scope for smallest change; manual fixed-commitment entry + import stub only |

---

## Action Items

1. **Create `.env` file** with available credentials (at minimum `GEMINI_API_KEY`, `SUPABASE_URL`, `SUPABASE_ANON_KEY`)
2. **Apply Supabase migrations** in order: 001 → 002 → 003
3. **Enable RLS** on all user-scoped tables after migration
4. **Check Vercel Python support** before Phase D deployment gate
5. **Human fills `docs/SANER_OBSERVATIONS.md`** before implementing features 7, 8, 11, 17 from audit section 5