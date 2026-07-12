package repository

import (
	"context"
	"errors"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
	"kakeibo/backend/internal/model"
)

var ErrNotFound = errors.New("not found")

type Repository struct{ db *pgxpool.Pool }

func New(db *pgxpool.Pool) *Repository { return &Repository{db: db} }

func (r *Repository) ListCategories(ctx context.Context, includeDeleted bool) ([]model.Category, error) {
	where := "WHERE deleted_at IS NULL"
	if includeDeleted {
		where = ""
	}
	rows, err := r.db.Query(ctx, `SELECT uuid,name,created_at,updated_at,deleted_at FROM categories `+where+` ORDER BY name`)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	items := []model.Category{}
	for rows.Next() {
		var x model.Category
		if err := rows.Scan(&x.UUID, &x.Name, &x.CreatedAt, &x.UpdatedAt, &x.DeletedAt); err != nil {
			return nil, err
		}
		items = append(items, x)
	}
	return items, rows.Err()
}
func (r *Repository) SaveCategory(ctx context.Context, x model.Category) (model.Category, error) {
	now := time.Now().UTC()
	if x.CreatedAt.IsZero() {
		x.CreatedAt = now
	}
	if x.UpdatedAt.IsZero() {
		x.UpdatedAt = now
	}
	err := r.db.QueryRow(ctx, `INSERT INTO categories(uuid,name,created_at,updated_at,deleted_at) VALUES($1,$2,$3,$4,$5)
	ON CONFLICT(uuid) DO UPDATE SET name=EXCLUDED.name,updated_at=EXCLUDED.updated_at,deleted_at=EXCLUDED.deleted_at
	WHERE categories.updated_at < EXCLUDED.updated_at RETURNING uuid,name,created_at,updated_at,deleted_at`, x.UUID, x.Name, x.CreatedAt, x.UpdatedAt, x.DeletedAt).Scan(&x.UUID, &x.Name, &x.CreatedAt, &x.UpdatedAt, &x.DeletedAt)
	if errors.Is(err, pgx.ErrNoRows) {
		return r.GetCategory(ctx, x.UUID)
	}
	return x, err
}
func (r *Repository) GetCategory(ctx context.Context, id string) (model.Category, error) {
	var x model.Category
	err := r.db.QueryRow(ctx, `SELECT uuid,name,created_at,updated_at,deleted_at FROM categories WHERE uuid=$1`, id).Scan(&x.UUID, &x.Name, &x.CreatedAt, &x.UpdatedAt, &x.DeletedAt)
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

func (r *Repository) ListExpenses(ctx context.Context, from, to, category string, includeDeleted bool) ([]model.Expense, error) {
	rows, err := r.db.Query(ctx, `SELECT uuid,expense_date,amount,category_uuid,memo,created_at,updated_at,deleted_at FROM expenses
	WHERE (NULLIF($1,'') IS NULL OR expense_date >= NULLIF($1,'')::date) AND (NULLIF($2,'') IS NULL OR expense_date <= NULLIF($2,'')::date) AND (NULLIF($3,'') IS NULL OR category_uuid=NULLIF($3,'')::uuid) AND ($4 OR deleted_at IS NULL) ORDER BY expense_date DESC,created_at DESC`, from, to, category, includeDeleted)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	items := []model.Expense{}
	for rows.Next() {
		var x model.Expense
		var d time.Time
		if err := rows.Scan(&x.UUID, &d, &x.Amount, &x.CategoryUUID, &x.Memo, &x.CreatedAt, &x.UpdatedAt, &x.DeletedAt); err != nil {
			return nil, err
		}
		x.Date = d.Format("2006-01-02")
		items = append(items, x)
	}
	return items, rows.Err()
}
func (r *Repository) GetExpense(ctx context.Context, id string) (model.Expense, error) {
	var x model.Expense
	var d time.Time
	err := r.db.QueryRow(ctx, `SELECT uuid,expense_date,amount,category_uuid,memo,created_at,updated_at,deleted_at FROM expenses WHERE uuid=$1`, id).Scan(&x.UUID, &d, &x.Amount, &x.CategoryUUID, &x.Memo, &x.CreatedAt, &x.UpdatedAt, &x.DeletedAt)
	if errors.Is(err, pgx.ErrNoRows) {
		err = ErrNotFound
	}
	x.Date = d.Format("2006-01-02")
	return x, err
}
func (r *Repository) SaveExpense(ctx context.Context, x model.Expense) (model.Expense, error) {
	now := time.Now().UTC()
	if x.CreatedAt.IsZero() {
		x.CreatedAt = now
	}
	if x.UpdatedAt.IsZero() {
		x.UpdatedAt = now
	}
	var d time.Time
	err := r.db.QueryRow(ctx, `INSERT INTO expenses(uuid,expense_date,amount,category_uuid,memo,created_at,updated_at,deleted_at) VALUES($1,$2,$3,$4,$5,$6,$7,$8)
	ON CONFLICT(uuid) DO UPDATE SET expense_date=EXCLUDED.expense_date,amount=EXCLUDED.amount,category_uuid=EXCLUDED.category_uuid,memo=EXCLUDED.memo,updated_at=EXCLUDED.updated_at,deleted_at=EXCLUDED.deleted_at
	WHERE expenses.updated_at < EXCLUDED.updated_at RETURNING uuid,expense_date,amount,category_uuid,memo,created_at,updated_at,deleted_at`, x.UUID, x.Date, x.Amount, x.CategoryUUID, x.Memo, x.CreatedAt, x.UpdatedAt, x.DeletedAt).Scan(&x.UUID, &d, &x.Amount, &x.CategoryUUID, &x.Memo, &x.CreatedAt, &x.UpdatedAt, &x.DeletedAt)
	if errors.Is(err, pgx.ErrNoRows) {
		return r.GetExpense(ctx, x.UUID)
	}
	x.Date = d.Format("2006-01-02")
	return x, err
}
func (r *Repository) DeleteExpense(ctx context.Context, id string) error {
	tag, err := r.db.Exec(ctx, `UPDATE expenses SET deleted_at=NOW(),updated_at=NOW() WHERE uuid=$1 AND deleted_at IS NULL`, id)
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
	rows, err := r.db.Query(ctx, `SELECT uuid,name,created_at,updated_at,deleted_at FROM categories WHERE updated_at >= $1`, since)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	a := []model.Category{}
	for rows.Next() {
		var x model.Category
		if err := rows.Scan(&x.UUID, &x.Name, &x.CreatedAt, &x.UpdatedAt, &x.DeletedAt); err != nil {
			return nil, err
		}
		a = append(a, x)
	}
	return a, rows.Err()
}
func (r *Repository) changedExpenses(ctx context.Context, since time.Time) ([]model.Expense, error) {
	rows, err := r.db.Query(ctx, `SELECT uuid,expense_date,amount,category_uuid,memo,created_at,updated_at,deleted_at FROM expenses WHERE updated_at >= $1`, since)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	a := []model.Expense{}
	for rows.Next() {
		var x model.Expense
		var d time.Time
		if err := rows.Scan(&x.UUID, &d, &x.Amount, &x.CategoryUUID, &x.Memo, &x.CreatedAt, &x.UpdatedAt, &x.DeletedAt); err != nil {
			return nil, err
		}
		x.Date = d.Format("2006-01-02")
		a = append(a, x)
	}
	return a, rows.Err()
}
