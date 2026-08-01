ALTER TABLE categories
  ADD COLUMN IF NOT EXISTS transaction_type TEXT NOT NULL DEFAULT 'expense';

ALTER TABLE categories
  DROP CONSTRAINT IF EXISTS categories_transaction_type_check;

ALTER TABLE categories
  ADD CONSTRAINT categories_transaction_type_check
  CHECK (transaction_type IN ('expense', 'income'));

CREATE INDEX IF NOT EXISTS categories_type_name_idx
  ON categories(transaction_type, name);
