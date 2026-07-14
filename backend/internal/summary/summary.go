package summary

import (
	"errors"
	"time"

	"kakeibo/backend/internal/model"
)

type Period string

const (
	Day   Period = "day"
	Week  Period = "week"
	Month Period = "month"
	Year  Period = "year"
)

type Bucket struct {
	Key          string `json:"key"`
	From         string `json:"from"`
	To           string `json:"to"`
	Amount       int64  `json:"amount"`
	ExpenseCount int    `json:"expense_count"`
}

type Result struct {
	Period       Period   `json:"period"`
	AnchorDate   string   `json:"anchor_date"`
	From         string   `json:"from"`
	To           string   `json:"to"`
	TotalAmount  int64    `json:"total_amount"`
	ExpenseCount int      `json:"expense_count"`
	Buckets      []Bucket `json:"buckets"`
}

func Parse(periodValue, dateValue string) (Period, time.Time, error) {
	period := Period(periodValue)
	if period != Day && period != Week && period != Month && period != Year {
		return "", time.Time{}, errors.New("period must be day, week, month or year")
	}
	anchor, err := time.Parse("2006-01-02", dateValue)
	if err != nil {
		return "", time.Time{}, errors.New("date must be YYYY-MM-DD")
	}
	return period, anchor, nil
}

func Bounds(period Period, anchor time.Time) (time.Time, time.Time) {
	anchor = dateOnly(anchor)
	switch period {
	case Day:
		return anchor, anchor
	case Week:
		mondayOffset := (int(anchor.Weekday()) + 6) % 7
		from := anchor.AddDate(0, 0, -mondayOffset)
		return from, from.AddDate(0, 0, 6)
	case Month:
		from := time.Date(anchor.Year(), anchor.Month(), 1, 0, 0, 0, 0, time.UTC)
		return from, from.AddDate(0, 1, -1)
	case Year:
		from := time.Date(anchor.Year(), time.January, 1, 0, 0, 0, 0, time.UTC)
		return from, time.Date(anchor.Year(), time.December, 31, 0, 0, 0, 0, time.UTC)
	default:
		return anchor, anchor
	}
}

func Build(period Period, anchor time.Time, expenses []model.Expense) Result {
	from, to := Bounds(period, anchor)
	buckets := emptyBuckets(period, from, to)
	bucketIndexes := make(map[string]int, len(buckets))
	for index, bucket := range buckets {
		bucketIndexes[bucket.Key] = index
	}

	result := Result{
		Period:     period,
		AnchorDate: dateOnly(anchor).Format("2006-01-02"),
		From:       from.Format("2006-01-02"),
		To:         to.Format("2006-01-02"),
		Buckets:    buckets,
	}
	for _, expense := range expenses {
		date, err := time.Parse("2006-01-02", expense.Date)
		if err != nil || expense.DeletedAt != nil || date.Before(from) || date.After(to) {
			continue
		}
		key := expense.Date
		if period == Year {
			key = expense.Date[:7]
		}
		index, exists := bucketIndexes[key]
		if !exists {
			continue
		}
		result.Buckets[index].Amount += expense.Amount
		result.Buckets[index].ExpenseCount++
		result.TotalAmount += expense.Amount
		result.ExpenseCount++
	}
	return result
}

func emptyBuckets(period Period, from, to time.Time) []Bucket {
	if period == Year {
		buckets := make([]Bucket, 0, 12)
		for month := time.January; month <= time.December; month++ {
			monthFrom := time.Date(from.Year(), month, 1, 0, 0, 0, 0, time.UTC)
			monthTo := monthFrom.AddDate(0, 1, -1)
			buckets = append(buckets, Bucket{
				Key:  monthFrom.Format("2006-01"),
				From: monthFrom.Format("2006-01-02"),
				To:   monthTo.Format("2006-01-02"),
			})
		}
		return buckets
	}

	buckets := make([]Bucket, 0, int(to.Sub(from).Hours()/24)+1)
	for date := from; !date.After(to); date = date.AddDate(0, 0, 1) {
		value := date.Format("2006-01-02")
		buckets = append(buckets, Bucket{Key: value, From: value, To: value})
	}
	return buckets
}

func dateOnly(value time.Time) time.Time {
	return time.Date(value.Year(), value.Month(), value.Day(), 0, 0, 0, 0, time.UTC)
}
