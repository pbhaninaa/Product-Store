-- Run AFTER you create the public Storage bucket named: product-images
-- (Supabase → Storage → New bucket → name product-images → Public: ON)

drop policy if exists "Public read product images" on storage.objects;
drop policy if exists "Auth upload product images" on storage.objects;
drop policy if exists "Auth delete product images" on storage.objects;

create policy "Public read product images"
  on storage.objects for select
  using (bucket_id = 'product-images');

create policy "Auth upload product images"
  on storage.objects for insert
  to authenticated
  with check (bucket_id = 'product-images');

create policy "Auth delete product images"
  on storage.objects for delete
  to authenticated
  using (bucket_id = 'product-images');
