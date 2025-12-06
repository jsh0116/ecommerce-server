-- Migration: Add missing columns to coupons table
-- Date: 2025-12-06
-- Description: Add discount_rate and other missing columns to support coupon refactoring

-- Check if discount_rate column exists before adding
SET @col_exists = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'coupons'
      AND COLUMN_NAME = 'discount_rate'
);

-- Add discount_rate column if it doesn't exist
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE coupons ADD COLUMN discount_rate INT NOT NULL DEFAULT 0 AFTER discount',
    'SELECT "Column discount_rate already exists" AS message'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Check if min_order_amount column exists before adding
SET @col_exists = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'coupons'
      AND COLUMN_NAME = 'min_order_amount'
);

SET @sql = IF(@col_exists = 0,
    'ALTER TABLE coupons ADD COLUMN min_order_amount BIGINT NOT NULL DEFAULT 0 AFTER discount_rate',
    'SELECT "Column min_order_amount already exists" AS message'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Check if max_discount_amount column exists before adding
SET @col_exists = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'coupons'
      AND COLUMN_NAME = 'max_discount_amount'
);

SET @sql = IF(@col_exists = 0,
    'ALTER TABLE coupons ADD COLUMN max_discount_amount BIGINT NULL AFTER min_order_amount',
    'SELECT "Column max_discount_amount already exists" AS message'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
