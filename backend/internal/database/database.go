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
			return pool, nil
		}
		if pool != nil {
			pool.Close()
		}
		time.Sleep(time.Second)
	}
	return nil, fmt.Errorf("connect database: %w", err)
}
