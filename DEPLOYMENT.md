# Deployment Guide — Product Store

**Last updated:** June 2026

Vue 2 storefront + admin. **Supabase** (Postgres, Storage, Auth, Realtime) + **Vercel** (or Netlify) for the SPA.

## Pre-deployment checklist

- [ ] Supabase project created
- [ ] `supabase/all.sql` (or `schema.sql` + related scripts) run in SQL Editor
- [ ] Storage bucket `product-images` created (public)
- [ ] Email auth enabled; admin user created for `/admin`
- [ ] Vercel env vars use `VUE_APP_*` prefix (not `VITE_*`)

## Supabase

Run scripts in order (or use `supabase/all.sql`):

1. `supabase/schema.sql` — products table, RLS, Realtime
2. `supabase/product-stock.sql` — if upgrading legacy DB
3. `supabase/orders.sql` — checkout and EFT flow
4. `supabase/storage-policies.sql` — after creating bucket `product-images` (public)

Configure **Authentication → Email** and create an admin user.

## Vercel

| Setting | Value |
|---------|--------|
| Root Directory | repo root (folder containing `package.json`) |
| Build | `npm run build` |
| Output | `dist` |

| Variable | Description |
|----------|-------------|
| `VUE_APP_SUPABASE_URL` | `https://YOUR_PROJECT.supabase.co` |
| `VUE_APP_SUPABASE_ANON_KEY` | Anon JWT from Project Settings → API |

Enable for **Production** and **Preview**. **Redeploy** after adding or changing variables.

## Local development

```bash
npm install
copy .env.example .env
# set VUE_APP_SUPABASE_URL and VUE_APP_SUPABASE_ANON_KEY
npm run serve
```

Restart dev server after `.env` changes.

## Rollback

Redeploy previous Vercel build. Database migrations are forward-only unless you restore from Supabase backup.

See [README.md](README.md) and [frontend/README.md](frontend/README.md).
