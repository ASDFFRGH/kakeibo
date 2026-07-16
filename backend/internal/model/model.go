package model

import "time"

const (
	TransactionTypeExpense = "expense"
	TransactionTypeIncome  = "income"
)

type Category struct {
	UUID      string     `json:"uuid"`
	Name      string     `json:"name"`
	CreatedAt time.Time  `json:"created_at"`
	UpdatedAt time.Time  `json:"updated_at"`
	DeletedAt *time.Time `json:"deleted_at"`
}

type Expense struct {
	UUID         string     `json:"uuid"`
	Date         string     `json:"date"`
	Amount       int64      `json:"amount"`
	Type         string     `json:"type"`
	CategoryUUID string     `json:"category_uuid"`
	Memo         string     `json:"memo"`
	CreatedAt    time.Time  `json:"created_at"`
	UpdatedAt    time.Time  `json:"updated_at"`
	DeletedAt    *time.Time `json:"deleted_at"`
}

type SyncRequest struct {
	LastSyncedAt *time.Time `json:"last_synced_at"`
	Categories   []Category `json:"categories"`
	Expenses     []Expense  `json:"expenses"`
}

type SyncData struct {
	Categories []Category `json:"categories"`
	Expenses   []Expense  `json:"expenses"`
}

type TrashData struct {
	Categories []Category `json:"categories"`
	Expenses   []Expense  `json:"expenses"`
}
