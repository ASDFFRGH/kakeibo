package repository

import (
	"context"
	"errors"
	"sort"
	"strings"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
	"kakeibo/backend/internal/model"
)

var ErrNotFound = errors.New("not found")

type Repository struct{ db *pgxpool.Pool }

func New(db *pgxpool.Pool) *Repository { return &Repository{db: db} }

type expenseQuerier interface {
	QueryRow(context.Context, string, ...any) pgx.Row
}

type gamblingQuerier interface {
	Query(context.Context, string, ...any) (pgx.Rows, error)
	QueryRow(context.Context, string, ...any) pgx.Row
}

func (r *Repository) ListCategories(ctx context.Context, includeDeleted bool) ([]model.Category, error) {
	where := "WHERE deleted_at IS NULL"
	if includeDeleted {
		where = ""
	}
	rows, err := r.db.Query(ctx, `SELECT uuid,name,transaction_type,created_at,updated_at,deleted_at FROM categories `+where+` ORDER BY name`)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	items := []model.Category{}
	for rows.Next() {
		var x model.Category
		if err := rows.Scan(&x.UUID, &x.Name, &x.Type, &x.CreatedAt, &x.UpdatedAt, &x.DeletedAt); err != nil {
			return nil, err
		}
		items = append(items, x)
	}
	return items, rows.Err()
}
func (r *Repository) SaveCategory(ctx context.Context, x model.Category) (model.Category, error) {
	if x.Type == "" {
		existing, err := r.GetCategory(ctx, x.UUID)
		if err == nil {
			x.Type = existing.Type
		} else if errors.Is(err, ErrNotFound) {
			x.Type = model.TransactionTypeExpense
		} else {
			return model.Category{}, err
		}
	}
	now := time.Now().UTC()
	if x.CreatedAt.IsZero() {
		x.CreatedAt = now
	}
	if x.UpdatedAt.IsZero() {
		x.UpdatedAt = now
	}
	err := r.db.QueryRow(ctx, `INSERT INTO categories(uuid,name,transaction_type,created_at,updated_at,deleted_at) VALUES($1,$2,$3,$4,$5,$6)
	ON CONFLICT(uuid) DO UPDATE SET name=EXCLUDED.name,transaction_type=EXCLUDED.transaction_type,updated_at=EXCLUDED.updated_at,deleted_at=EXCLUDED.deleted_at
	WHERE categories.updated_at < EXCLUDED.updated_at RETURNING uuid,name,transaction_type,created_at,updated_at,deleted_at`, x.UUID, x.Name, x.Type, x.CreatedAt, x.UpdatedAt, x.DeletedAt).Scan(&x.UUID, &x.Name, &x.Type, &x.CreatedAt, &x.UpdatedAt, &x.DeletedAt)
	if errors.Is(err, pgx.ErrNoRows) {
		return r.GetCategory(ctx, x.UUID)
	}
	return x, err
}
func (r *Repository) GetCategory(ctx context.Context, id string) (model.Category, error) {
	var x model.Category
	err := r.db.QueryRow(ctx, `SELECT uuid,name,transaction_type,created_at,updated_at,deleted_at FROM categories WHERE uuid=$1`, id).Scan(&x.UUID, &x.Name, &x.Type, &x.CreatedAt, &x.UpdatedAt, &x.DeletedAt)
	if errors.Is(err, pgx.ErrNoRows) {
		err = ErrNotFound
	}
	return x, err
}
func (r *Repository) DeleteCategory(ctx context.Context, id string) error {
	tag, err := r.db.Exec(ctx, `UPDATE categories SET deleted_at=NOW(),updated_at=NOW() WHERE uuid=$1 AND deleted_at IS NULL`, id)
	if err == nil && tag.RowsAffected() == 0 {
		return ErrNotFound
	}
	return err
}

func (r *Repository) ListDeletedCategories(ctx context.Context) ([]model.Category, error) {
	rows, err := r.db.Query(ctx, `SELECT uuid,name,transaction_type,created_at,updated_at,deleted_at FROM categories WHERE deleted_at IS NOT NULL ORDER BY deleted_at DESC`)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	items := []model.Category{}
	for rows.Next() {
		var x model.Category
		if err := rows.Scan(&x.UUID, &x.Name, &x.Type, &x.CreatedAt, &x.UpdatedAt, &x.DeletedAt); err != nil {
			return nil, err
		}
		items = append(items, x)
	}
	return items, rows.Err()
}

func (r *Repository) RestoreCategory(ctx context.Context, id string) error {
	tag, err := r.db.Exec(ctx, `UPDATE categories SET deleted_at=NULL,updated_at=NOW() WHERE uuid=$1 AND deleted_at IS NOT NULL`, id)
	if err == nil && tag.RowsAffected() == 0 {
		return ErrNotFound
	}
	return err
}

func (r *Repository) ListExpenses(ctx context.Context, from, to, category string, includeDeleted bool) ([]model.Expense, error) {
	rows, err := r.db.Query(ctx, `SELECT uuid,expense_date,amount,transaction_type,category_uuid,memo,created_at,updated_at,deleted_at FROM expenses
	WHERE (NULLIF($1,'') IS NULL OR expense_date >= NULLIF($1,'')::date) AND (NULLIF($2,'') IS NULL OR expense_date <= NULLIF($2,'')::date) AND (NULLIF($3,'') IS NULL OR category_uuid=NULLIF($3,'')::uuid) AND ($4 OR deleted_at IS NULL) ORDER BY expense_date DESC,created_at DESC`, from, to, category, includeDeleted)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	items := []model.Expense{}
	for rows.Next() {
		var x model.Expense
		var d time.Time
		if err := rows.Scan(&x.UUID, &d, &x.Amount, &x.Type, &x.CategoryUUID, &x.Memo, &x.CreatedAt, &x.UpdatedAt, &x.DeletedAt); err != nil {
			return nil, err
		}
		x.Date = d.Format("2006-01-02")
		items = append(items, x)
	}
	return items, rows.Err()
}
func (r *Repository) GetExpense(ctx context.Context, id string) (model.Expense, error) {
	return getExpense(ctx, r.db, id)
}
func getExpense(ctx context.Context, db expenseQuerier, id string) (model.Expense, error) {
	var x model.Expense
	var d time.Time
	err := db.QueryRow(ctx, `SELECT uuid,expense_date,amount,transaction_type,category_uuid,memo,created_at,updated_at,deleted_at FROM expenses WHERE uuid=$1`, id).Scan(&x.UUID, &d, &x.Amount, &x.Type, &x.CategoryUUID, &x.Memo, &x.CreatedAt, &x.UpdatedAt, &x.DeletedAt)
	if errors.Is(err, pgx.ErrNoRows) {
		err = ErrNotFound
	}
	if err == nil {
		x.Date = d.Format("2006-01-02")
	}
	return x, err
}
func (r *Repository) SaveExpense(ctx context.Context, x model.Expense) (model.Expense, error) {
	return saveExpense(ctx, r.db, x)
}
func saveExpense(ctx context.Context, db expenseQuerier, x model.Expense) (model.Expense, error) {
	if x.Type == "" {
		existing, err := getExpense(ctx, db, x.UUID)
		if err == nil {
			x.Type = existing.Type
		} else if errors.Is(err, ErrNotFound) {
			x.Type = model.TransactionTypeExpense
		} else {
			return model.Expense{}, err
		}
	}
	now := time.Now().UTC()
	if x.CreatedAt.IsZero() {
		x.CreatedAt = now
	}
	if x.UpdatedAt.IsZero() {
		x.UpdatedAt = now
	}
	var d time.Time
	err := db.QueryRow(ctx, `INSERT INTO expenses(uuid,expense_date,amount,transaction_type,category_uuid,memo,created_at,updated_at,deleted_at) VALUES($1,$2,$3,$4,$5,$6,$7,$8,$9)
	ON CONFLICT(uuid) DO UPDATE SET expense_date=EXCLUDED.expense_date,amount=EXCLUDED.amount,transaction_type=EXCLUDED.transaction_type,category_uuid=EXCLUDED.category_uuid,memo=EXCLUDED.memo,updated_at=EXCLUDED.updated_at,deleted_at=EXCLUDED.deleted_at
	WHERE expenses.updated_at < EXCLUDED.updated_at RETURNING uuid,expense_date,amount,transaction_type,category_uuid,memo,created_at,updated_at,deleted_at`, x.UUID, x.Date, x.Amount, x.Type, x.CategoryUUID, x.Memo, x.CreatedAt, x.UpdatedAt, x.DeletedAt).Scan(&x.UUID, &d, &x.Amount, &x.Type, &x.CategoryUUID, &x.Memo, &x.CreatedAt, &x.UpdatedAt, &x.DeletedAt)
	if errors.Is(err, pgx.ErrNoRows) {
		return getExpense(ctx, db, x.UUID)
	}
	if err == nil {
		x.Date = d.Format("2006-01-02")
	}
	return x, err
}
func (r *Repository) SaveExpenses(ctx context.Context, expenses []model.Expense) ([]model.Expense, error) {
	tx, err := r.db.Begin(ctx)
	if err != nil {
		return nil, err
	}
	defer func() { _ = tx.Rollback(ctx) }()

	saved := make([]model.Expense, 0, len(expenses))
	for _, expense := range expenses {
		item, saveErr := saveExpense(ctx, tx, expense)
		if saveErr != nil {
			return nil, saveErr
		}
		saved = append(saved, item)
	}
	if err := tx.Commit(ctx); err != nil {
		return nil, err
	}
	return saved, nil
}
func (r *Repository) DeleteExpense(ctx context.Context, id string) error {
	tag, err := r.db.Exec(ctx, `UPDATE expenses SET deleted_at=NOW(),updated_at=NOW() WHERE uuid=$1 AND deleted_at IS NULL`, id)
	if err == nil && tag.RowsAffected() == 0 {
		return ErrNotFound
	}
	return err
}

func (r *Repository) ListDeletedExpenses(ctx context.Context) ([]model.Expense, error) {
	rows, err := r.db.Query(ctx, `SELECT uuid,expense_date,amount,transaction_type,category_uuid,memo,created_at,updated_at,deleted_at FROM expenses WHERE deleted_at IS NOT NULL ORDER BY deleted_at DESC`)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	items := []model.Expense{}
	for rows.Next() {
		var x model.Expense
		var d time.Time
		if err := rows.Scan(&x.UUID, &d, &x.Amount, &x.Type, &x.CategoryUUID, &x.Memo, &x.CreatedAt, &x.UpdatedAt, &x.DeletedAt); err != nil {
			return nil, err
		}
		x.Date = d.Format("2006-01-02")
		items = append(items, x)
	}
	return items, rows.Err()
}

func (r *Repository) RestoreExpense(ctx context.Context, id string) error {
	tag, err := r.db.Exec(ctx, `WITH target AS (
		SELECT category_uuid FROM expenses WHERE uuid=$1 AND deleted_at IS NOT NULL
	), restored_category AS (
		UPDATE categories SET deleted_at=NULL,updated_at=NOW()
		WHERE uuid=(SELECT category_uuid FROM target) AND deleted_at IS NOT NULL
	)
	UPDATE expenses SET deleted_at=NULL,updated_at=NOW() WHERE uuid=$1 AND deleted_at IS NOT NULL`, id)
	if err == nil && tag.RowsAffected() == 0 {
		return ErrNotFound
	}
	return err
}
func (r *Repository) Changes(ctx context.Context, since time.Time) (model.SyncData, error) {
	cats, err := r.changedCategories(ctx, since)
	if err != nil {
		return model.SyncData{}, err
	}
	exps, err := r.changedExpenses(ctx, since)
	return model.SyncData{Categories: cats, Expenses: exps}, err
}
func (r *Repository) changedCategories(ctx context.Context, since time.Time) ([]model.Category, error) {
	rows, err := r.db.Query(ctx, `SELECT uuid,name,transaction_type,created_at,updated_at,deleted_at FROM categories WHERE updated_at >= $1`, since)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	a := []model.Category{}
	for rows.Next() {
		var x model.Category
		if err := rows.Scan(&x.UUID, &x.Name, &x.Type, &x.CreatedAt, &x.UpdatedAt, &x.DeletedAt); err != nil {
			return nil, err
		}
		a = append(a, x)
	}
	return a, rows.Err()
}
func (r *Repository) changedExpenses(ctx context.Context, since time.Time) ([]model.Expense, error) {
	rows, err := r.db.Query(ctx, `SELECT uuid,expense_date,amount,transaction_type,category_uuid,memo,created_at,updated_at,deleted_at FROM expenses WHERE updated_at >= $1`, since)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	a := []model.Expense{}
	for rows.Next() {
		var x model.Expense
		var d time.Time
		if err := rows.Scan(&x.UUID, &d, &x.Amount, &x.Type, &x.CategoryUUID, &x.Memo, &x.CreatedAt, &x.UpdatedAt, &x.DeletedAt); err != nil {
			return nil, err
		}
		x.Date = d.Format("2006-01-02")
		a = append(a, x)
	}
	return a, rows.Err()
}

func (r *Repository) ListGamblingRecords(ctx context.Context, from, to, gameType string, includeDeleted bool) ([]model.GamblingRecord, error) {
	rows, err := r.db.Query(ctx, `SELECT uuid,gambling_date,stake_amount,payout_amount,game_type,memo,created_at,updated_at,deleted_at FROM gambling_records
	WHERE (NULLIF($1,'') IS NULL OR gambling_date >= NULLIF($1,'')::date)
	AND (NULLIF($2,'') IS NULL OR gambling_date <= NULLIF($2,'')::date)
	AND (NULLIF($3,'') IS NULL OR game_type=NULLIF($3,''))
	AND ($4 OR deleted_at IS NULL)
	ORDER BY gambling_date DESC,created_at DESC`, from, to, gameType, includeDeleted)
	if err != nil {
		return nil, err
	}
	return scanGamblingRecords(rows)
}

func (r *Repository) GetGamblingRecord(ctx context.Context, id string) (model.GamblingRecord, error) {
	return getGamblingRecord(ctx, r.db, id)
}

func getGamblingRecord(ctx context.Context, db gamblingQuerier, id string) (model.GamblingRecord, error) {
	var x model.GamblingRecord
	var d time.Time
	err := db.QueryRow(ctx, `SELECT uuid,gambling_date,stake_amount,payout_amount,game_type,memo,created_at,updated_at,deleted_at
	FROM gambling_records WHERE uuid=$1`, id).Scan(
		&x.UUID, &d, &x.StakeAmount, &x.PayoutAmount, &x.GameType, &x.Memo,
		&x.CreatedAt, &x.UpdatedAt, &x.DeletedAt,
	)
	if errors.Is(err, pgx.ErrNoRows) {
		err = ErrNotFound
	}
	if err == nil {
		x.Date = d.Format("2006-01-02")
	}
	return x, err
}

func (r *Repository) SaveGamblingRecord(ctx context.Context, x model.GamblingRecord) (model.GamblingRecord, error) {
	return saveGamblingRecord(ctx, r.db, x)
}

func saveGamblingRecord(ctx context.Context, db gamblingQuerier, x model.GamblingRecord) (model.GamblingRecord, error) {
	now := time.Now().UTC()
	if x.CreatedAt.IsZero() {
		x.CreatedAt = now
	}
	if x.UpdatedAt.IsZero() {
		x.UpdatedAt = now
	}
	x.GameType = strings.TrimSpace(x.GameType)

	var d time.Time
	err := db.QueryRow(ctx, `INSERT INTO gambling_records(
		uuid,gambling_date,stake_amount,payout_amount,game_type,memo,created_at,updated_at,deleted_at
	) VALUES($1,$2,$3,$4,$5,$6,$7,$8,$9)
	ON CONFLICT(uuid) DO UPDATE SET
		gambling_date=EXCLUDED.gambling_date,
		stake_amount=EXCLUDED.stake_amount,
		payout_amount=EXCLUDED.payout_amount,
		game_type=EXCLUDED.game_type,
		memo=EXCLUDED.memo,
		updated_at=EXCLUDED.updated_at,
		deleted_at=EXCLUDED.deleted_at
	WHERE gambling_records.updated_at < EXCLUDED.updated_at
	RETURNING uuid,gambling_date,stake_amount,payout_amount,game_type,memo,created_at,updated_at,deleted_at`,
		x.UUID, x.Date, x.StakeAmount, x.PayoutAmount, x.GameType, x.Memo,
		x.CreatedAt, x.UpdatedAt, x.DeletedAt,
	).Scan(
		&x.UUID, &d, &x.StakeAmount, &x.PayoutAmount, &x.GameType, &x.Memo,
		&x.CreatedAt, &x.UpdatedAt, &x.DeletedAt,
	)
	if errors.Is(err, pgx.ErrNoRows) {
		return getGamblingRecord(ctx, db, x.UUID)
	}
	if err == nil {
		x.Date = d.Format("2006-01-02")
	}
	return x, err
}

func (r *Repository) DeleteGamblingRecord(ctx context.Context, id string) error {
	tag, err := r.db.Exec(ctx, `UPDATE gambling_records SET deleted_at=NOW(),updated_at=NOW()
	WHERE uuid=$1 AND deleted_at IS NULL`, id)
	if err == nil && tag.RowsAffected() == 0 {
		return ErrNotFound
	}
	return err
}

func (r *Repository) ListDeletedGamblingRecords(ctx context.Context) ([]model.GamblingRecord, error) {
	rows, err := r.db.Query(ctx, `SELECT uuid,gambling_date,stake_amount,payout_amount,game_type,memo,created_at,updated_at,deleted_at
	FROM gambling_records WHERE deleted_at IS NOT NULL ORDER BY deleted_at DESC`)
	if err != nil {
		return nil, err
	}
	return scanGamblingRecords(rows)
}

func (r *Repository) RestoreGamblingRecord(ctx context.Context, id string) error {
	tag, err := r.db.Exec(ctx, `UPDATE gambling_records SET deleted_at=NULL,updated_at=NOW()
	WHERE uuid=$1 AND deleted_at IS NOT NULL`, id)
	if err == nil && tag.RowsAffected() == 0 {
		return ErrNotFound
	}
	return err
}

// SyncGamblingRecords saves the complete upload atomically and returns only
// changes at or before the response cursor. Canonical server winners for every
// uploaded UUID are included even when their timestamps fall outside that range.
func (r *Repository) SyncGamblingRecords(
	ctx context.Context,
	records []model.GamblingRecord,
	since time.Time,
) (time.Time, []string, model.GamblingSyncData, error) {
	tx, err := r.db.Begin(ctx)
	if err != nil {
		return time.Time{}, nil, model.GamblingSyncData{}, err
	}
	defer func() { _ = tx.Rollback(ctx) }()

	synced := make([]string, 0, len(records))
	canonical := make(map[string]model.GamblingRecord, len(records))
	for _, record := range records {
		saved, saveErr := saveGamblingRecord(ctx, tx, record)
		if saveErr != nil {
			return time.Time{}, nil, model.GamblingSyncData{}, saveErr
		}
		synced = append(synced, record.UUID)
		canonical[record.UUID] = saved
	}

	var cutoff time.Time
	if err := tx.QueryRow(ctx, `SELECT clock_timestamp()`).Scan(&cutoff); err != nil {
		return time.Time{}, nil, model.GamblingSyncData{}, err
	}
	changed, err := changedGamblingRecords(ctx, tx, since, cutoff)
	if err != nil {
		return time.Time{}, nil, model.GamblingSyncData{}, err
	}

	positions := make(map[string]int, len(changed))
	for i, record := range changed {
		positions[record.UUID] = i
	}
	for id, record := range canonical {
		if i, exists := positions[id]; exists {
			changed[i] = record
		} else {
			changed = append(changed, record)
		}
	}
	sort.Slice(changed, func(i, j int) bool {
		if changed[i].UpdatedAt.Equal(changed[j].UpdatedAt) {
			return changed[i].UUID < changed[j].UUID
		}
		return changed[i].UpdatedAt.Before(changed[j].UpdatedAt)
	})

	if err := tx.Commit(ctx); err != nil {
		return time.Time{}, nil, model.GamblingSyncData{}, err
	}
	return cutoff.UTC(), synced, model.GamblingSyncData{Records: changed}, nil
}

func changedGamblingRecords(
	ctx context.Context,
	db gamblingQuerier,
	since, cutoff time.Time,
) ([]model.GamblingRecord, error) {
	rows, err := db.Query(ctx, `SELECT uuid,gambling_date,stake_amount,payout_amount,game_type,memo,created_at,updated_at,deleted_at
	FROM gambling_records WHERE updated_at >= $1 AND updated_at <= $2
	ORDER BY updated_at,uuid`, since, cutoff)
	if err != nil {
		return nil, err
	}
	return scanGamblingRecords(rows)
}

func scanGamblingRecords(rows pgx.Rows) ([]model.GamblingRecord, error) {
	defer rows.Close()
	items := []model.GamblingRecord{}
	for rows.Next() {
		var x model.GamblingRecord
		var d time.Time
		if err := rows.Scan(
			&x.UUID, &d, &x.StakeAmount, &x.PayoutAmount, &x.GameType, &x.Memo,
			&x.CreatedAt, &x.UpdatedAt, &x.DeletedAt,
		); err != nil {
			return nil, err
		}
		x.Date = d.Format("2006-01-02")
		items = append(items, x)
	}
	return items, rows.Err()
}
