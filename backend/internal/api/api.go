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
	repo         *repository.Repository
	summaryRepo  summaryExpenseRepository
	trashRepo    trashRepository
	gamblingRepo gamblingRepository
	cors         string
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

type gamblingRepository interface {
	ListGamblingRecords(ctx context.Context, from, to, gameType string, includeDeleted bool) ([]model.GamblingRecord, error)
	GetGamblingRecord(ctx context.Context, id string) (model.GamblingRecord, error)
	SaveGamblingRecord(ctx context.Context, record model.GamblingRecord) (model.GamblingRecord, error)
	DeleteGamblingRecord(ctx context.Context, id string) error
	ListDeletedGamblingRecords(ctx context.Context) ([]model.GamblingRecord, error)
	RestoreGamblingRecord(ctx context.Context, id string) error
	SyncGamblingRecords(ctx context.Context, records []model.GamblingRecord, since time.Time) (time.Time, []string, model.GamblingSyncData, error)
}

func New(repo *repository.Repository, cors string) http.Handler {
	a := &API{repo: repo, summaryRepo: repo, trashRepo: repo, gamblingRepo: repo, cors: cors}
	return a.middleware(a.routes())
}

func (a *API) routes() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("GET /health", func(w http.ResponseWriter, r *http.Request) { ok(w, map[string]string{"status": "ok"}) })
	mux.HandleFunc("GET /api/v1/categories", a.listCategories)
	mux.HandleFunc("POST /api/v1/categories", a.createCategory)
	mux.HandleFunc("PUT /api/v1/categories/{uuid}", a.updateCategory)
	mux.HandleFunc("DELETE /api/v1/categories/{uuid}", a.deleteCategory)
	mux.HandleFunc("GET /api/v1/expenses", a.listExpenses)
	mux.HandleFunc("GET /api/v1/expenses/{uuid}", a.getExpense)
	mux.HandleFunc("POST /api/v1/expenses", a.createExpense)
	mux.HandleFunc("POST /api/v1/expenses/batch", a.createExpensesBatch)
	mux.HandleFunc("PUT /api/v1/expenses/{uuid}", a.updateExpense)
	mux.HandleFunc("DELETE /api/v1/expenses/{uuid}", a.deleteExpense)
	mux.HandleFunc("GET /api/v1/trash", a.listTrash)
	mux.HandleFunc("POST /api/v1/categories/{uuid}/restore", a.restoreCategory)
	mux.HandleFunc("POST /api/v1/expenses/{uuid}/restore", a.restoreExpense)
	mux.HandleFunc("GET /api/v1/summaries", a.getSummary)
	mux.HandleFunc("POST /api/v1/sync", a.sync)
	mux.HandleFunc("GET /api/v1/gambling/records", a.listGamblingRecords)
	mux.HandleFunc("POST /api/v1/gambling/records", a.createGamblingRecord)
	mux.HandleFunc("GET /api/v1/gambling/records/{uuid}", a.getGamblingRecord)
	mux.HandleFunc("PUT /api/v1/gambling/records/{uuid}", a.updateGamblingRecord)
	mux.HandleFunc("DELETE /api/v1/gambling/records/{uuid}", a.deleteGamblingRecord)
	mux.HandleFunc("GET /api/v1/gambling/trash", a.listGamblingTrash)
	mux.HandleFunc("POST /api/v1/gambling/records/{uuid}/restore", a.restoreGamblingRecord)
	mux.HandleFunc("POST /api/v1/gambling/sync", a.syncGamblingRecords)
	return mux
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
func validGamblingUUID(s string) bool {
	if len(s) != 36 || s[8] != '-' || s[13] != '-' || s[18] != '-' || s[23] != '-' {
		return false
	}
	for i := range s {
		if i == 8 || i == 13 || i == 18 || i == 23 {
			continue
		}
		if !strings.ContainsRune("0123456789abcdefABCDEF", rune(s[i])) {
			return false
		}
	}
	return true
}
func validCategory(x model.Category) error {
	if !validUUID(x.UUID) || strings.TrimSpace(x.Name) == "" {
		return errors.New("uuid and name are required")
	}
	if x.Type != "" && x.Type != model.TransactionTypeExpense && x.Type != model.TransactionTypeIncome {
		return errors.New("type must be expense or income")
	}
	return nil
}
func validExpense(x model.Expense) error {
	if !validUUID(x.UUID) || !validUUID(x.CategoryUUID) || x.Amount <= 0 {
		return errors.New("valid uuid, category_uuid and positive amount are required")
	}
	if x.Type != "" && x.Type != model.TransactionTypeExpense && x.Type != model.TransactionTypeIncome {
		return errors.New("type must be expense or income")
	}
	if _, err := time.Parse("2006-01-02", x.Date); err != nil {
		return errors.New("date must be YYYY-MM-DD")
	}
	return nil
}
func normalizeGamblingRecord(x model.GamblingRecord) model.GamblingRecord {
	x.GameType = strings.TrimSpace(x.GameType)
	return x
}
func validGamblingRecord(x model.GamblingRecord) error {
	if !validGamblingUUID(x.UUID) {
		return errors.New("valid uuid is required")
	}
	parsedDate, err := time.Parse("2006-01-02", x.Date)
	if err != nil || parsedDate.Format("2006-01-02") != x.Date {
		return errors.New("date must be YYYY-MM-DD")
	}
	if x.StakeAmount < 0 || x.PayoutAmount < 0 || (x.StakeAmount == 0 && x.PayoutAmount == 0) {
		return errors.New("stake_amount and payout_amount must be non-negative and at least one must be positive")
	}
	if strings.TrimSpace(x.GameType) == "" {
		return errors.New("game_type is required")
	}
	return nil
}
func validOptionalDate(value string) bool {
	if value == "" {
		return true
	}
	parsed, err := time.Parse("2006-01-02", value)
	return err == nil && parsed.Format("2006-01-02") == value
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
func (a *API) createExpensesBatch(w http.ResponseWriter, r *http.Request) {
	var req model.BatchExpenseRequest
	if decode(r, &req) != nil || len(req.Expenses) == 0 || len(req.Expenses) > 100 {
		fail(w, 400, "invalid expense batch")
		return
	}
	for _, x := range req.Expenses {
		if validExpense(x) != nil {
			fail(w, 400, "invalid expense in batch")
			return
		}
	}
	saved, err := a.repo.SaveExpenses(r.Context(), req.Expenses)
	if err != nil {
		a.handleErr(w, err)
		return
	}
	write(w, 201, map[string]any{"success": true, "data": saved})
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

func (a *API) listGamblingRecords(w http.ResponseWriter, r *http.Request) {
	query := r.URL.Query()
	from, to := query.Get("from"), query.Get("to")
	if !validOptionalDate(from) || !validOptionalDate(to) {
		fail(w, http.StatusBadRequest, "from and to must be YYYY-MM-DD")
		return
	}
	records, err := a.gamblingRepo.ListGamblingRecords(
		r.Context(), from, to, strings.TrimSpace(query.Get("game_type")), false,
	)
	if err != nil {
		a.handleErr(w, err)
		return
	}
	ok(w, records)
}

func (a *API) getGamblingRecord(w http.ResponseWriter, r *http.Request) {
	id := r.PathValue("uuid")
	if !validGamblingUUID(id) {
		fail(w, http.StatusBadRequest, "invalid gambling record uuid")
		return
	}
	record, err := a.gamblingRepo.GetGamblingRecord(r.Context(), id)
	if err != nil {
		a.handleErr(w, err)
		return
	}
	ok(w, record)
}

func (a *API) createGamblingRecord(w http.ResponseWriter, r *http.Request) {
	var record model.GamblingRecord
	if decode(r, &record) != nil {
		fail(w, http.StatusBadRequest, "invalid gambling record")
		return
	}
	record = normalizeGamblingRecord(record)
	if validGamblingRecord(record) != nil {
		fail(w, http.StatusBadRequest, "invalid gambling record")
		return
	}
	saved, err := a.gamblingRepo.SaveGamblingRecord(r.Context(), record)
	if err != nil {
		a.handleErr(w, err)
		return
	}
	write(w, http.StatusCreated, map[string]any{"success": true, "data": saved})
}

func (a *API) updateGamblingRecord(w http.ResponseWriter, r *http.Request) {
	var record model.GamblingRecord
	if decode(r, &record) != nil {
		fail(w, http.StatusBadRequest, "invalid gambling record")
		return
	}
	record.UUID = r.PathValue("uuid")
	record = normalizeGamblingRecord(record)
	if validGamblingRecord(record) != nil {
		fail(w, http.StatusBadRequest, "invalid gambling record")
		return
	}
	record.UpdatedAt = time.Now().UTC()
	saved, err := a.gamblingRepo.SaveGamblingRecord(r.Context(), record)
	if err != nil {
		a.handleErr(w, err)
		return
	}
	ok(w, saved)
}

func (a *API) deleteGamblingRecord(w http.ResponseWriter, r *http.Request) {
	id := r.PathValue("uuid")
	if !validGamblingUUID(id) {
		fail(w, http.StatusBadRequest, "invalid gambling record uuid")
		return
	}
	if err := a.gamblingRepo.DeleteGamblingRecord(r.Context(), id); err != nil {
		a.handleErr(w, err)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

func (a *API) listGamblingTrash(w http.ResponseWriter, r *http.Request) {
	records, err := a.gamblingRepo.ListDeletedGamblingRecords(r.Context())
	if err != nil {
		a.handleErr(w, err)
		return
	}
	ok(w, records)
}

func (a *API) restoreGamblingRecord(w http.ResponseWriter, r *http.Request) {
	id := r.PathValue("uuid")
	if !validGamblingUUID(id) {
		fail(w, http.StatusBadRequest, "invalid gambling record uuid")
		return
	}
	if err := a.gamblingRepo.RestoreGamblingRecord(r.Context(), id); err != nil {
		a.handleErr(w, err)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

func (a *API) syncGamblingRecords(w http.ResponseWriter, r *http.Request) {
	var request model.GamblingSyncRequest
	if decode(r, &request) != nil {
		fail(w, http.StatusBadRequest, "invalid gambling sync request")
		return
	}
	for i := range request.Records {
		request.Records[i] = normalizeGamblingRecord(request.Records[i])
		if validGamblingRecord(request.Records[i]) != nil {
			fail(w, http.StatusBadRequest, "invalid gambling record in sync")
			return
		}
	}
	since := time.Unix(0, 0).UTC()
	if request.LastSyncedAt != nil {
		since = request.LastSyncedAt.UTC()
	}
	serverTime, synced, data, err := a.gamblingRepo.SyncGamblingRecords(r.Context(), request.Records, since)
	if err != nil {
		a.handleErr(w, err)
		return
	}
	write(w, http.StatusOK, map[string]any{
		"success":     true,
		"server_time": serverTime,
		"synced":      synced,
		"data":        data,
	})
}
