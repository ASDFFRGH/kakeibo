package database

import (
	"strings"
	"testing"
)

func TestGamblingRuntimeMigrationContainsRequiredSchema(t *testing.T) {
	required := []string{
		"CREATE TABLE IF NOT EXISTS gambling_records",
		"stake_amount BIGINT NOT NULL CHECK (stake_amount >= 0)",
		"payout_amount BIGINT NOT NULL CHECK (payout_amount >= 0)",
		"CHECK (stake_amount > 0 OR payout_amount > 0)",
		"game_type TEXT NOT NULL CHECK (length(trim(game_type)) > 0)",
		"CREATE INDEX IF NOT EXISTS gambling_records_updated_at_idx",
	}
	for _, fragment := range required {
		if !strings.Contains(createGamblingRecords, fragment) {
			t.Errorf("runtime migration is missing %q", fragment)
		}
	}
}
