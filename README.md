# Product Site (Vue 2 + Supabase)

Storefront with **`/`** (grid) and **`/admin`** (uploads). Data: **Postgres** + **Storage** + **Email auth**. Live updates via **Realtime** on table **`products`**.

## Run locally

```bash
npm install
copy .env.example .env
```

Set in **`.env`** (Vue CLI uses **`VUE_APP_`**, not `VITE_`):

- **`VUE_APP_SUPABASE_URL`** — Project **URL** (`https://xxxxx.supabase.co`)
- **`VUE_APP_SUPABASE_ANON_KEY`** — **anon public** JWT (`eyJ...`) from **Project Settings → API**, or the dashboard **publishable** key if your project uses it.

After editing `.env`, **stop** the dev server (**Ctrl+C**) and run **`npm run serve` again** — variables are baked in when the build starts.

**Deployed site (Vercel / Netlify / etc.):** Add the **same two variables** in the host’s **Environment Variables** UI, then **redeploy**. If they’re missing there, production will always show “Supabase is not configured” even if `.env` works locally.

```bash
npm run serve
```

---

## What to do in Supabase (order matters)

Do these in the [Supabase Dashboard](https://supabase.com/dashboard) for **your** project.

### 1) Database — table + RLS + Realtime

1. Open **SQL Editor** → **New query**.
2. Copy the full contents of **`supabase/schema.sql`** in this repo → paste → **Run**.
3. If the last line errors with **already member of publication** → that’s OK (Realtime already includes `products`). If Realtime still doesn’t work: **Database** → **Publications** (or **Replication**) → ensure **`products`** is published to **`supabase_realtime`**.

### 2) Storage — bucket (before storage SQL)

1. **Storage** → **New bucket**.
2. **Name:** **`product-images`** (must match `STORAGE_BUCKET` in `src/supabase.js`).
3. **Public bucket:** **ON** (required). That makes each file’s **public URL** work from **any website, app, or chat preview** — same as a normal image on the internet. If the bucket is private, URLs will not load for visitors.
4. Create the bucket.

### 3) Storage — policies

1. **SQL Editor** → new query.
2. Copy **`supabase/storage-policies.sql`** → paste → **Run**.

### 4) Authentication

1. **Authentication** → **Providers** → **Email** → enable → save.
2. **Authentication** → **Users** → **Add user** → set email + password (this is your **`/admin`** login).

### 5) Verify

1. **`npm run serve`** (restart after any `.env` change).
2. Open **`/`** — no yellow config warning if `.env` is correct.
3. Open **`/admin`** — sign in → upload image + name + price → product appears on **`/`**.

---

## Security (short)

- **Products:** anyone can **read**; only **authenticated** users can **write** (`schema.sql`).
- **Storage:** anyone can **read** `product-images`; only **authenticated** users can **upload/delete** (`storage-policies.sql`).
- Never put the **database password** or **service role** key in `.env` for this Vue app (browser).

---

## Deploy

```bash
npm run build
```

Host **`dist/`** on Netlify / Vercel / Cloudflare Pages. Set **`VUE_APP_SUPABASE_URL`** and **`VUE_APP_SUPABASE_ANON_KEY`** in the host’s env, then rebuild.

### Vercel

1. Push the repo to GitHub/GitLab/Bitbucket (or use Vercel CLI).
2. **Import project** in [Vercel](https://vercel.com) → set **Root Directory** to **`product-site`** if the repo root is `website` and the app lives in that folder.
3. **Framework preset:** Other (or leave default).
4. **Build Command:** `npm run build`
5. **Output Directory:** `dist`
6. **Environment Variables** (Production + Preview):
   - `VUE_APP_SUPABASE_URL` = `https://YOUR_REF.supabase.co`
   - `VUE_APP_SUPABASE_ANON_KEY` = your anon / publishable key  
   Optional: `VUE_APP_SITE_NAME`.
7. Deploy. This repo includes **`vercel.json`** so **`/admin`** and other routes work with Vue Router history mode.

---

## Troubleshooting

| Issue | What to check |
|--------|----------------|
| Yellow “Supabase is not configured” | `.env` keys, restart `npm run serve` |
| Upload / delete **permission denied** | Run both SQL files; signed in on `/admin` |
| Images broken or only work on your site | Bucket **`product-images`** must be **Public**; run **`storage-policies.sql`** so **anyone** can **`select`** (read) objects in that bucket |
| List not live | Realtime publication includes **`products`** |
| Auth fails | **Email** provider on; user exists; try **anon `eyJ...`** key if publishable key fails |
