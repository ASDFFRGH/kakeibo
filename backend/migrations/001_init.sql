CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS categories (
  uuid UUID PRIMARY KEY,
  name TEXT NOT NULL CHECK (length(trim(name)) > 0),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  deleted_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS expenses (
  uuid UUID PRIMARY KEY,
  expense_date DATE NOT NULL,
  amount BIGINT NOT NULL CHECK (amount > 0),
  category_uuid UUID NOT NULL REFERENCES categories(uuid),
  memo TEXT NOT NULL DEFAULT '',
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  deleted_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS expenses_date_idx ON expenses(expense_date DESC);
CREATE INDEX IF NOT EXISTS expenses_updated_at_idx ON expenses(updated_at);
CREATE INDEX IF NOT EXISTS categories_updated_at_idx ON categories(updated_at);

INSERT INTO categories(uuid,name) VALUES
  ('10000000-0000-4000-8000-000000000001','食費'),
  ('10000000-0000-4000-8000-000000000002','日用品'),
  ('10000000-0000-4000-8000-000000000003','交通費')
ON CONFLICT DO NOTHING;
