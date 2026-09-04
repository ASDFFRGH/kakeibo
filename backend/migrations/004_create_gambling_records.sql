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
