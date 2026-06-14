-- Envers audits the payments table, so the audit mirror needs the new column too
-- (V10 added it only to the live table). Audit columns are nullable.
ALTER TABLE payments_aud ADD COLUMN gateway_order_id VARCHAR(255);
