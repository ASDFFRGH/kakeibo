package database

import (
	"context"
	"fmt"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"
)

func Open(ctx context.Context, url string) (*pgxpool.Pool, error) {
	var pool *pgxpool.Pool
	var err error
	for i := 0; i < 20; i++ {
		pool, err = pgxpool.New(ctx, url)
		if err == nil {
			err = pool.Ping(ctx)
		}
		if err == nil {
			if err = migrate(ctx, pool); err == nil {
				return pool, nil
			}
			pool.Close()
			return nil, fmt.Errorf("migrate database: %w", err)
		}
		if pool != nil {
			pool.Close()
		}
		time.Sleep(time.Second)
	}
	return nil, fmt.Errorf("connect database: %w", err)
}

func migrate(ctx context.Context, pool *pgxpool.Pool) error {
	_, err := pool.Exec(ctx, addCategoryTransactionType+createGamblingRecords)
	return err
}

const addCategoryTransactionType = `
ALTER TABLE categories
  ADD COLUMN IF NOT EXISTS transaction_type TEXT NOT NULL DEFAULT 'expense';

ALTER TABLE categories
  DROP CONSTRAINT IF EXISTS categories_transaction_type_check;

ALTER TABLE categories
  ADD CONSTRAINT categories_transaction_type_check
  CHECK (transaction_type IN ('expense', 'income'));

CREATE INDEX IF NOT EXISTS categories_type_name_idx
  ON categories(transaction_type, name);
`

const createGamblingRecords = `
CREATE TABLE IF NOT EXISTS gambling_records (
  uuid UUID PRIMARY KEY,
  gambling_date DATE NOT NULL,
  stake_amount BIGINT NOT NULL CHECK (stake_amount >= 0),
  payout_amount BIGINT NOT NULL CHECK (payout_amount >= 0),
  game_type TEXT NOT NULL CHECK (length(trim(game_type)) > 0),
  memo TEXT NOT NULL DEFAULT '',
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  deleted_at TIMESTAMPTZ,
  CONSTRAINT gambling_records_nonzero_amount_check
    CHECK (stake_amount > 0 OR payout_amount > 0)
);

CREATE INDEX IF NOT EXISTS gambling_records_date_idx
  ON gambling_records(gambling_date DESC);

CREATE INDEX IF NOT EXISTS gambling_records_updated_at_idx
  ON gambling_records(updated_at);

CREATE INDEX IF NOT EXISTS gambling_records_game_type_date_idx
  ON gambling_records(game_type, gambling_date DESC);
`
