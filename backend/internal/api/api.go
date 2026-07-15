package api

import (
	"context"
	"encoding/json"
	"errors"
	"io"
	"log"
	"net/http"
	"strings"
	"time"

	"kakeibo/backend/internal/model"
	"kakeibo/backend/internal/repository"
	"kakeibo/backend/internal/summary"
)

type API struct {
	repo        *repository.Repository
	summaryRepo summaryExpenseRepository
	trashRepo   trashRepository
	cors        string
}

type summaryExpenseRepository interface {
	ListExpenses(ctx context.Context, from, to, category string, includeDeleted bool) ([]model.Expense, error)
}

type trashRepository interface {
	ListDeletedCategories(ctx context.Context) ([]model.Category, error)
	ListDeletedExpenses(ctx context.Context) ([]model.Expense, error)
	RestoreCategory(ctx context.Context, id string) error
	RestoreExpense(ctx context.Context, id string) error
}

func New(repo *repository.Repository, cors string) http.Handler {
	a := &API{repo: repo, summaryRepo: repo, trashRepo: repo, cors: cors}
	mux := http.NewServeMux()
	mux.HandleFunc("GET /health", func(w http.ResponseWriter, r *http.Request) { ok(w, map[string]string{"status": "ok"}) })
	mux.HandleFunc("GET /api/v1/categories", a.listCategories)
	mux.HandleFunc("POST /api/v1/categories", a.createCategory)
	mux.HandleFunc("PUT /api/v1/categories/{uuid}", a.updateCategory)
	mux.HandleFunc("DELETE /api/v1/categories/{uuid}", a.deleteCategory)
	mux.HandleFunc("GET /api/v1/expenses", a.listExpenses)
	mux.HandleFunc("GET /api/v1/expenses/{uuid}", a.getExpense)
	mux.HandleFunc("POST /api/v1/expenses", a.createExpense)
	mux.HandleFunc("PUT /api/v1/expenses/{uuid}", a.updateExpense)
	mux.HandleFunc("DELETE /api/v1/expenses/{uuid}", a.deleteExpense)
	mux.HandleFunc("GET /api/v1/trash", a.listTrash)
	mux.HandleFunc("POST /api/v1/categories/{uuid}/restore", a.restoreCategory)
	mux.HandleFunc("POST /api/v1/expenses/{uuid}/restore", a.restoreExpense)
	mux.HandleFunc("GET /api/v1/summaries", a.getSummary)
	mux.HandleFunc("POST /api/v1/sync", a.sync)
	return a.middleware(mux)
}
func (a *API) middleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		origin := a.cors
		if origin == "" {
			origin = "http://localhost:3000"
		}
		w.Header().Set("Access-Control-Allow-Origin", origin)
		w.Header().Set("Access-Control-Allow-Headers", "Content-Type")
		w.Header().Set("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS")
		if r.Method == "OPTIONS" {
			w.WriteHeader(204)
			return
		}
		next.ServeHTTP(w, r)
	})
}
func ok(w http.ResponseWriter, data any) {
	write(w, 200, map[string]any{"success": true, "data": data})
}
func write(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(v)
}
func fail(w http.ResponseWriter, status int, msg string) {
	write(w, status, map[string]any{"success": false, "message": msg})
}
func decode(r *http.Request, v any) error {
	d := json.NewDecoder(io.LimitReader(r.Body, 1<<20))
	d.DisallowUnknownFields()
	return d.Decode(v)
}
func validUUID(s string) bool { return len(s) == 36 && strings.Count(s, "-") == 4 }
func validCategory(x model.Category) error {
	if !validUUID(x.UUID) || strings.TrimSpace(x.Name) == "" {
		return errors.New("uuid and name are required")
	}
	return nil
}
func validExpense(x model.Expense) error {
	if !validUUID(x.UUID) || !validUUID(x.CategoryUUID) || x.Amount <= 0 {
		return errors.New("valid uuid, category_uuid and positive amount are required")
	}
	if _, err := time.Parse("2006-01-02", x.Date); err != nil {
		return errors.New("date must be YYYY-MM-DD")
	}
	return nil
}
func (a *API) handleErr(w http.ResponseWriter, err error) {
	if errors.Is(err, repository.ErrNotFound) {
		fail(w, 404, "not found")
		return
	}
	log.Printf("ERROR %v", err)
	fail(w, 500, "internal server error")
}
func (a *API) listCategories(w http.ResponseWriter, r *http.Request) {
	x, e := a.repo.ListCategories(r.Context(), false)
	if e != nil {
		a.handleErr(w, e)
		return
	}
	ok(w, x)
}
func (a *API) createCategory(w http.ResponseWriter, r *http.Request) {
	var x model.Category
	if e := decode(r, &x); e != nil || validCategory(x) != nil {
		fail(w, 400, "invalid category")
		return
	}
	x, e := a.repo.SaveCategory(r.Context(), x)
	if e != nil {
		a.handleErr(w, e)
		return
	}
	write(w, 201, map[string]any{"success": true, "data": x})
}
func (a *API) updateCategory(w http.ResponseWriter, r *http.Request) {
	var x model.Category
	if decode(r, &x) != nil {
		fail(w, 400, "invalid category")
		return
	}
	x.UUID = r.PathValue("uuid")
	if validCategory(x) != nil {
		fail(w, 400, "invalid category")
		return
	}
	x.UpdatedAt = time.Now().UTC()
	saved, e := a.repo.SaveCategory(r.Context(), x)
	if e != nil {
		a.handleErr(w, e)
		return
	}
	ok(w, saved)
}
func (a *API) deleteCategory(w http.ResponseWriter, r *http.Request) {
	if e := a.repo.DeleteCategory(r.Context(), r.PathValue("uuid")); e != nil {
		a.handleErr(w, e)
		return
	}
	w.WriteHeader(204)
}
func (a *API) listExpenses(w http.ResponseWriter, r *http.Request) {
	q := r.URL.Query()
	x, e := a.repo.ListExpenses(r.Context(), q.Get("from"), q.Get("to"), q.Get("category_uuid"), false)
	if e != nil {
		a.handleErr(w, e)
		return
	}
	ok(w, x)
}
func (a *API) getExpense(w http.ResponseWriter, r *http.Request) {
	x, e := a.repo.GetExpense(r.Context(), r.PathValue("uuid"))
	if e != nil {
		a.handleErr(w, e)
		return
	}
	ok(w, x)
}
func (a *API) createExpense(w http.ResponseWriter, r *http.Request) {
	var x model.Expense
	if decode(r, &x) != nil || validExpense(x) != nil {
		fail(w, 400, "invalid expense")
		return
	}
	x, e := a.repo.SaveExpense(r.Context(), x)
	if e != nil {
		a.handleErr(w, e)
		return
	}
	write(w, 201, map[string]any{"success": true, "data": x})
}
func (a *API) updateExpense(w http.ResponseWriter, r *http.Request) {
	var x model.Expense
	if decode(r, &x) != nil {
		fail(w, 400, "invalid expense")
		return
	}
	x.UUID = r.PathValue("uuid")
	if validExpense(x) != nil {
		fail(w, 400, "invalid expense")
		return
	}
	x.UpdatedAt = time.Now().UTC()
	saved, e := a.repo.SaveExpense(r.Context(), x)
	if e != nil {
		a.handleErr(w, e)
		return
	}
	ok(w, saved)
}
func (a *API) deleteExpense(w http.ResponseWriter, r *http.Request) {
	if e := a.repo.DeleteExpense(r.Context(), r.PathValue("uuid")); e != nil {
		a.handleErr(w, e)
		return
	}
	w.WriteHeader(204)
}
func (a *API) listTrash(w http.ResponseWriter, r *http.Request) {
	categories, err := a.trashRepo.ListDeletedCategories(r.Context())
	if err != nil {
		a.handleErr(w, err)
		return
	}
	expenses, err := a.trashRepo.ListDeletedExpenses(r.Context())
	if err != nil {
		a.handleErr(w, err)
		return
	}
	ok(w, model.TrashData{Categories: categories, Expenses: expenses})
}
func (a *API) restoreCategory(w http.ResponseWriter, r *http.Request) {
	if err := a.trashRepo.RestoreCategory(r.Context(), r.PathValue("uuid")); err != nil {
		a.handleErr(w, err)
		return
	}
	w.WriteHeader(204)
}
func (a *API) restoreExpense(w http.ResponseWriter, r *http.Request) {
	if err := a.trashRepo.RestoreExpense(r.Context(), r.PathValue("uuid")); err != nil {
		a.handleErr(w, err)
		return
	}
	w.WriteHeader(204)
}
func (a *API) getSummary(w http.ResponseWriter, r *http.Request) {
	period, anchor, err := summary.Parse(r.URL.Query().Get("period"), r.URL.Query().Get("date"))
	if err != nil {
		fail(w, 400, err.Error())
		return
	}
	from, to := summary.Bounds(period, anchor)
	expenses, err := a.summaryRepo.ListExpenses(
		r.Context(),
		from.Format("2006-01-02"),
		to.Format("2006-01-02"),
		"",
		false,
	)
	if err != nil {
		a.handleErr(w, err)
		return
	}
	ok(w, summary.Build(period, anchor, expenses))
}
func (a *API) sync(w http.ResponseWriter, r *http.Request) {
	var req model.SyncRequest
	if decode(r, &req) != nil {
		fail(w, 400, "invalid sync request")
		return
	}
	synced := []string{}
	for _, x := range req.Categories {
		if validCategory(x) != nil {
			fail(w, 400, "invalid category in sync")
			return
		}
		if _, e := a.repo.SaveCategory(r.Context(), x); e != nil {
			a.handleErr(w, e)
			return
		}
		synced = append(synced, x.UUID)
	}
	for _, x := range req.Expenses {
		if validExpense(x) != nil {
			fail(w, 400, "invalid expense in sync")
			return
		}
		if _, e := a.repo.SaveExpense(r.Context(), x); e != nil {
			a.handleErr(w, e)
			return
		}
		synced = append(synced, x.UUID)
	}
	since := time.Unix(0, 0).UTC()
	if req.LastSyncedAt != nil {
		since = *req.LastSyncedAt
	}
	data, e := a.repo.Changes(r.Context(), since)
	if e != nil {
		a.handleErr(w, e)
		return
	}
	write(w, 200, map[string]any{"success": true, "server_time": time.Now().UTC(), "synced": synced, "data": data})
}
