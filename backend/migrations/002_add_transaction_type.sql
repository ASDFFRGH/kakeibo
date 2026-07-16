ALTER TABLE expenses
  ADD COLUMN IF NOT EXISTS transaction_type TEXT NOT NULL DEFAULT 'expense';

ALTER TABLE expenses
  DROP CONSTRAINT IF EXISTS expenses_transaction_type_check;

ALTER TABLE expenses
  ADD CONSTRAINT expenses_transaction_type_check
  CHECK (transaction_type IN ('expense', 'income'));

CREATE INDEX IF NOT EXISTS expenses_type_date_idx
  ON expenses(transaction_type, expense_date DESC);
