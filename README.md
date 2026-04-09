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

### Vercel (checklist)

1. **Vercel** → your project → **Settings** → **General** → **Root Directory**: leave empty if this repo is only this app; if the GitHub repo has a parent folder, set it to the folder that contains `package.json` (e.g. `product-site`).
2. **Settings** → **Environment Variables** — add exactly (names matter; use `VUE_APP_*`, not `VITE_*`):
   - `VUE_APP_SUPABASE_URL` = `https://YOUR_PROJECT.supabase.co`
   - `VUE_APP_SUPABASE_ANON_KEY` = anon JWT or publishable key from Supabase **Project Settings → API**
   - Enable **Production** and **Preview** (and **Development** if you use `vercel dev`).
3. **Deployments** → **⋯** on the latest deployment → **Redeploy** (required after adding or changing env vars).

```bash
npm run serve
```

---

## What to do in Supabase (order matters)

Do these in the [Supabase Dashboard](https://supabase.com/dashboard) for **your** project.

### All-in-one (recommended)

1. **SQL Editor** → **New query**.
2. Paste the full contents of **`supabase/all.sql`** → **Run** once.  
   That file includes **products** (+ stock migration), **orders** (+ `create_order` / `confirm_order_payment`), the **`product-images`** storage bucket, and **storage** policies.

Then skip ahead to **Authentication** and **Verify** below (you still need an email user for `/admin`).

### Or run scripts separately

### 1) Database — table + RLS + Realtime

1. Open **SQL Editor** → **New query**.
2. Copy the full contents of **`supabase/schema.sql`** in this repo → paste → **Run**.
3. `schema.sql` / `all.sql` only add **`products`** to **`supabase_realtime`** if it isn’t already there (re-runs won’t error). If Realtime still doesn’t work: **Database** → **Publications** → ensure **`products`** is in **`supabase_realtime`**.

4. **Stock:** New installs get **`products.stock`** from `schema.sql`. If your project already had `products` without that column, run **`supabase/product-stock.sql`**, then run **`supabase/orders.sql`** again (safe to re-run) so **`create_order`** checks stock and subtracts quantities after each order.

   **Error:** `Could not find the 'stock' column of 'products' in the schema cache` → run **`supabase/product-stock.sql`** in the SQL Editor (adds the column). Wait a moment, then retry; no app code change is required.

5. **Orders (checkout + EFT confirmation):** SQL Editor → run **`supabase/orders.sql`** once after `schema.sql`. Edit **`shop_settings`** (Table Editor, row `id = 1`) to set **delivery fee** and **EFT / bank instructions** text shown to customers.

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
3. Open **`/admin`** — sign in → add a product with **price** and **stock quantity** → it appears on **`/`**; adjust stock anytime under **Inventory**.
4. **`/`** → **Add** on products → **`/checkout`** → place order (prices are enforced by the `create_order` database function). **`/admin`** → **Orders** → **Print invoice** (opens a printable page; use the browser’s print dialog or **Save as PDF**). Confirm **EFT** after bank shows payment.

---

## Security (short)

- **Products:** anyone can **read** (including **stock** counts); only **authenticated** users can **write** or **change stock** (`schema.sql`). Checkout uses **`create_order`**, which subtracts sold quantities from **`products.stock`**.
- **Storage:** anyone can **read** `product-images`; only **authenticated** users can **upload/delete** (`storage-policies.sql`).
- **Orders:** customers only **create** orders via **`create_order`** (pending payment; stock is **not** reduced yet). Checkout checks that **`products.stock` minus quantities on other unpaid orders** covers the cart. **Staff** call **`confirm_order_payment`** (or the alias **`confirm_eft_payment`**) when EFT or cash is received; that RPC **subtracts stock** and sets **`payment_confirmed`**. Order rows are **not** readable anonymously—only admins see them (`orders.sql`).
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
| Upgraded `create_order` and stock looks wrong | Old behaviour subtracted stock at checkout. Unpaid orders may need **stock restored** or **confirm payment** once — see comment at top of `orders.sql` |
