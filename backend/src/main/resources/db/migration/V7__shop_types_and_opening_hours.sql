-- Third store type + salon booking shop hours (JSON array of {dayOfWeek,start,end})
ALTER TABLE shop_settings MODIFY COLUMN shop_type VARCHAR(48) NOT NULL DEFAULT 'normal_store';
UPDATE shop_settings SET shop_type = 'salon_and_store' WHERE shop_type = 'salon';
ALTER TABLE shop_settings ADD COLUMN opening_hours_json TEXT NULL;
