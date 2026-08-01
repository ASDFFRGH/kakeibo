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
	_, err := pool.Exec(ctx, addCategoryTransactionType)
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
