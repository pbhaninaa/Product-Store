-- Salon service listing images (store path + public URL like products)
ALTER TABLE salon_services ADD COLUMN image_url VARCHAR(2000) NOT NULL DEFAULT '';
ALTER TABLE salon_services ADD COLUMN image_path VARCHAR(1024) NOT NULL DEFAULT '';
