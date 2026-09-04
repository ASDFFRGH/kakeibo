package repository

import (
	"context"
	"errors"
	"os"
	"testing"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"
	"kakeibo/backend/internal/model"
)

const gamblingTestUUID = "50000000-0000-4000-8000-000000000001"

func openGamblingTestRepository(t *testing.T) (*Repository, *pgxpool.Pool) {
	t.Helper()
	databaseURL := os.Getenv("TEST_DATABASE_URL")
	if databaseURL == "" {
		t.Skip("TEST_DATABASE_URL is not set")
	}
	ctx := context.Background()
	pool, err := pgxpool.New(ctx, databaseURL)
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(pool.Close)
	migration, err := os.ReadFile("../../migrations/004_create_gambling_records.sql")
	if err != nil {
		t.Fatal(err)
	}
	if _, err := pool.Exec(ctx, string(migration)); err != nil {
		t.Fatalf("apply migration: %v", err)
	}
	if _, err := pool.Exec(ctx, `TRUNCATE gambling_records`); err != nil {
		t.Fatal(err)
	}
	return New(pool), pool
}

func gamblingRecordAt(updatedAt time.Time) model.GamblingRecord {
	return model.GamblingRecord{
		UUID:         gamblingTestUUID,
		Date:         "2026-09-04",
		StakeAmount:  5000,
		PayoutAmount: 8000,
		GameType:     "競馬",
		Memo:         "original",
		CreatedAt:    updatedAt.Add(-time.Hour),
		UpdatedAt:    updatedAt,
	}
}

func TestGamblingRepositoryLifecycleAndConflictResolution(t *testing.T) {
	repo, _ := openGamblingTestRepository(t)
	ctx := context.Background()
	baseTime := time.Now().UTC().Add(-2 * time.Hour).Truncate(time.Microsecond)
	original := gamblingRecordAt(baseTime)

	saved, err := repo.SaveGamblingRecord(ctx, original)
	if err != nil {
		t.Fatal(err)
	}
	if saved.UUID != original.UUID || saved.GameType != original.GameType {
		t.Fatalf("saved = %+v", saved)
	}

	older := original
	older.StakeAmount = 9999
	older.Memo = "older loses"
	older.UpdatedAt = baseTime.Add(-time.Minute)
	winner, err := repo.SaveGamblingRecord(ctx, older)
	if err != nil {
		t.Fatal(err)
	}
	if winner.StakeAmount != original.StakeAmount || winner.Memo != original.Memo {
		t.Fatalf("older update won: %+v", winner)
	}

	equal := original
	equal.StakeAmount = 7777
	equal.Memo = "equal loses"
	winner, err = repo.SaveGamblingRecord(ctx, equal)
	if err != nil {
		t.Fatal(err)
	}
	if winner.StakeAmount != original.StakeAmount || winner.Memo != original.Memo {
		t.Fatalf("equal update won: %+v", winner)
	}

	newer := original
	newer.StakeAmount = 6000
	newer.Memo = "newer wins"
	newer.UpdatedAt = baseTime.Add(time.Minute)
	winner, err = repo.SaveGamblingRecord(ctx, newer)
	if err != nil {
		t.Fatal(err)
	}
	if winner.StakeAmount != newer.StakeAmount || winner.Memo != newer.Memo {
		t.Fatalf("newer update lost: %+v", winner)
	}

	active, err := repo.ListGamblingRecords(ctx, "2026-09-01", "2026-09-30", "競馬", false)
	if err != nil || len(active) != 1 {
		t.Fatalf("active = %+v, err = %v", active, err)
	}
	if err := repo.DeleteGamblingRecord(ctx, original.UUID); err != nil {
		t.Fatal(err)
	}
	active, err = repo.ListGamblingRecords(ctx, "", "", "", false)
	if err != nil || len(active) != 0 {
		t.Fatalf("active after delete = %+v, err = %v", active, err)
	}
	trash, err := repo.ListDeletedGamblingRecords(ctx)
	if err != nil || len(trash) != 1 || trash[0].DeletedAt == nil {
		t.Fatalf("trash = %+v, err = %v", trash, err)
	}
	if err := repo.RestoreGamblingRecord(ctx, original.UUID); err != nil {
		t.Fatal(err)
	}
	restored, err := repo.GetGamblingRecord(ctx, original.UUID)
	if err != nil || restored.DeletedAt != nil {
		t.Fatalf("restored = %+v, err = %v", restored, err)
	}
}

func TestGamblingSyncIsAtomicAndReturnsCanonicalWinner(t *testing.T) {
	repo, _ := openGamblingTestRepository(t)
	ctx := context.Background()
	baseTime := time.Now().UTC().Add(-2 * time.Hour).Truncate(time.Microsecond)
	serverRecord := gamblingRecordAt(baseTime)
	if _, err := repo.SaveGamblingRecord(ctx, serverRecord); err != nil {
		t.Fatal(err)
	}

	olderUpload := serverRecord
	olderUpload.Memo = "client loses"
	olderUpload.UpdatedAt = baseTime.Add(-time.Minute)
	cutoff, synced, data, err := repo.SyncGamblingRecords(
		ctx, []model.GamblingRecord{olderUpload}, baseTime.Add(time.Hour),
	)
	if err != nil {
		t.Fatal(err)
	}
	if cutoff.IsZero() || len(synced) != 1 || synced[0] != serverRecord.UUID {
		t.Fatalf("cutoff = %s, synced = %#v", cutoff, synced)
	}
	if len(data.Records) != 1 || data.Records[0].Memo != serverRecord.Memo {
		t.Fatalf("canonical data = %+v", data)
	}

	good := gamblingRecordAt(baseTime.Add(time.Minute))
	good.UUID = "50000000-0000-4000-8000-000000000002"
	bad := gamblingRecordAt(baseTime.Add(2 * time.Minute))
	bad.UUID = "50000000-0000-4000-8000-000000000003"
	bad.StakeAmount, bad.PayoutAmount = 0, 0
	if _, _, _, err := repo.SyncGamblingRecords(ctx, []model.GamblingRecord{good, bad}, time.Unix(0, 0)); err == nil {
		t.Fatal("sync accepted a database-invalid record")
	}
	if _, err := repo.GetGamblingRecord(ctx, good.UUID); !errors.Is(err, ErrNotFound) {
		t.Fatalf("first record from failed sync was persisted: %v", err)
	}
}
