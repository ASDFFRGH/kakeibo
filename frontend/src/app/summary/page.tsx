"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import {
  CalendarDays,
  ChevronLeft,
  ChevronRight,
  List,
  RefreshCw,
  WalletCards,
} from "lucide-react";
import { api, Summary, SummaryPeriod } from "../../lib/api";
import { shiftAnchor } from "../../lib/summary-period.mjs";

const periods: { value: SummaryPeriod; label: string }[] = [
  { value: "day", label: "日別" },
  { value: "week", label: "週別" },
  { value: "month", label: "月別" },
  { value: "year", label: "年別" },
];
const today = () => new Date().toISOString().slice(0, 10);
const money = new Intl.NumberFormat("ja-JP", {
  style: "currency",
  currency: "JPY",
});

export default function SummaryPage() {
  const [period, setPeriod] = useState<SummaryPeriod>("month");
  const [anchor, setAnchor] = useState(today());
  const [summary, setSummary] = useState<Summary | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      setSummary(await api.summary(period, anchor));
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "読込に失敗しました");
    } finally {
      setLoading(false);
    }
  }, [anchor, period]);

  useEffect(() => {
    void load();
  }, [load]);

  const maximumAmount =
    summary?.buckets.reduce(
      (maximum, bucket) => Math.max(maximum, bucket.amount),
      1,
    ) ?? 1;

  return (
    <main>
      <header>
        <div className="brand">
          <WalletCards />
          <span>My 家計簿</span>
        </div>
        <Link className="secondary" href="/">
          <List size={18} />
          支出一覧
        </Link>
      </header>

      <section className="summary-heading">
        <div>
          <span>期間サマリー</span>
          <h1>支出の推移</h1>
        </div>
        <button className="icon" title="再読み込み" onClick={() => void load()}>
          <RefreshCw size={18} />
        </button>
      </section>

      <section className="summary-controls" aria-label="集計期間">
        <div className="summary-tabs">
          {periods.map((option) => (
            <button
              className={period === option.value ? "active" : ""}
              key={option.value}
              onClick={() => setPeriod(option.value)}
            >
              {option.label}
            </button>
          ))}
        </div>
        <div className="period-navigation">
          <button
            aria-label="前の期間"
            onClick={() => setAnchor(shiftAnchor(anchor, period, -1))}
          >
            <ChevronLeft />
          </button>
          <label>
            <CalendarDays size={18} />
            <input
              type="date"
              value={anchor}
              onChange={(event) => setAnchor(event.target.value)}
            />
          </label>
          <button
            aria-label="次の期間"
            onClick={() => setAnchor(shiftAnchor(anchor, period, 1))}
          >
            <ChevronRight />
          </button>
        </div>
      </section>

      {error && <div className="error">{error}</div>}
      {loading ? (
        <div className="summary-empty">読み込み中...</div>
      ) : summary ? (
        <>
          <section className="summary-result">
            <span>{rangeLabel(summary)}</span>
            <strong>{money.format(summary.total_amount)}</strong>
            <small>{summary.expense_count}件</small>
          </section>
          {summary.expense_count === 0 ? (
            <div className="summary-empty">この期間の支出はありません</div>
          ) : (
            <section className="summary-breakdown">
              {summary.buckets.map((bucket) => (
                <div className="summary-bucket" key={bucket.key}>
                  <div>
                    <span>{bucketLabel(summary.period, bucket.key)}</span>
                    <small>{bucket.expense_count}件</small>
                  </div>
                  <div className="summary-bar-track" aria-hidden="true">
                    <span
                      style={{
                        width: `${(bucket.amount / maximumAmount) * 100}%`,
                      }}
                    />
                  </div>
                  <strong>{money.format(bucket.amount)}</strong>
                </div>
              ))}
            </section>
          )}
        </>
      ) : null}
    </main>
  );
}

function rangeLabel(summary: Summary) {
  if (summary.from === summary.to) return summary.from;
  return `${summary.from} 〜 ${summary.to}`;
}

function bucketLabel(period: SummaryPeriod, key: string) {
  if (period === "year") return `${Number(key.slice(5, 7))}月`;
  return new Date(`${key}T00:00:00`).toLocaleDateString("ja-JP", {
    month: "numeric",
    day: "numeric",
    weekday: "short",
  });
}
