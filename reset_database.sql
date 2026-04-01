-- ============================================================
-- BAS Database Reset Script
-- Run this in Supabase Dashboard > SQL Editor BEFORE launching
-- the updated app. The app will re-seed automatically.
-- ============================================================

-- Drop all tables (order matters due to foreign keys)
DROP TABLE IF EXISTS procurement_orders CASCADE;
DROP TABLE IF EXISTS sale_items CASCADE;
DROP TABLE IF EXISTS sales CASCADE;
DROP TABLE IF EXISTS oos_requests CASCADE;
DROP TABLE IF EXISTS books CASCADE;
DROP TABLE IF EXISTS app_logs CASCADE;
DROP TABLE IF EXISTS users CASCADE;

-- Verify everything is gone
SELECT table_name FROM information_schema.tables
WHERE table_schema = 'public' AND table_type = 'BASE TABLE';
