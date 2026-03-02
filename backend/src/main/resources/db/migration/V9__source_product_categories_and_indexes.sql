-- 1) Add categories
ALTER TABLE fdp_grocery.source_product
ADD COLUMN IF NOT EXISTS categories text[];

-- 2) Debt item: speed up staples CTE lookup (upc filter)
CREATE INDEX IF NOT EXISTS idx_source_product_upc
ON fdp_grocery.source_product (upc);

-- 3) Debt item: speed up name search (ILIKE/LIKE %query% via trigram)
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_source_product_name_trgm
ON fdp_grocery.source_product
USING gin (name gin_trgm_ops);