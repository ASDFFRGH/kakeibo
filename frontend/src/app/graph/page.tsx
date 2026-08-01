"use client";

import {
  CalendarDays,
  ChevronLeft,
  ChevronRight,
  LineChart,
  List,
  WalletCards,
} from "lucide-react";
import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import { api, Category, Expense } from "../../lib/api";
import { localToday } from "../../lib/calendar-date.mjs";

const money = new Intl.NumberFormat("ja-JP", {
  style: "currency",
  currency: "JPY",
  maximumFractionDigits: 0,
});

type GraphScope = "overall" | "category";

export default function GraphPage() {
  const current = localToday();
  const [year, setYear] = useState(Number(current.slice(0, 4)));
  const [selectedMonth, setSelectedMonth] = useState(
    Number(current.slice(5, 7)),
  );
  const [scope, setScope] = useState<GraphScope>("overall");
  const [categoryUuid, setCategoryUuid] = useState("");
  const [categories, setCategories] = useState<Category[]>([]);
  const [expenses, setExpenses] = useState<Expense[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const [loadedCategories, loadedExpenses] = await Promise.all([
        api.categories(),
        api.expenses(),
      ]);
      setCategories(loadedCategories);
      setExpenses(loadedExpenses);
      setCategoryUuid((value) => value || loadedCategories[0]?.uuid || "");
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "読込に失敗しました");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const points = useMemo(
    () =>
      Array.from({ length: 12 }, (_, index) => {
        const month = index + 1;
        const prefix = `${year}-${String(month).padStart(2, "0")}`;
        const target = expenses.filter(
          (expense) =>
            expense.date.startsWith(prefix) &&
            (scope === "overall" || expense.category_uuid === categoryUuid),
        );
        const income = target
          .filter((expense) => expense.type === "income")
          .reduce((sum, expense) => sum + expense.amount, 0);
        const expense = target
          .filter((item) => item.type !== "income")
          .reduce((sum, item) => sum + item.amount, 0);
        return { month, income, expense, balance: income - expense };
      }),
    [categoryUuid, expenses, scope, year],
  );
  const selected = points[selectedMonth - 1];

  return (
    <main className="graph-page">
      <header>
        <div className="brand">
          <WalletCards />
          <span>My 家計簿</span>
        </div>
        <Link className="secondary" href="/">
          <List size={18} />
          カレンダー
        </Link>
      </header>

      <section className="graph-heading">
        <div>
          <span>グラフ</span>
          <h1>月ごとの収支</h1>
        </div>
        <LineChart size={30} />
      </section>

      <section className="graph-controls">
        <div className="summary-tabs" aria-label="集計範囲">
          <button
            className={scope === "overall" ? "active" : ""}
            onClick={() => setScope("overall")}
          >
            全体
          </button>
          <button
            className={scope === "category" ? "active" : ""}
            onClick={() => setScope("category")}
          >
            カテゴリ別
          </button>
        </div>
        {scope === "category" && (
          <label>
            表示するカテゴリ
            <select
              value={categoryUuid}
              onChange={(event) => setCategoryUuid(event.target.value)}
            >
              {categories.map((category) => (
                <option value={category.uuid} key={category.uuid}>
                  {category.type === "income" ? "収入" : "支出"}・
                  {category.name}
                </option>
              ))}
            </select>
          </label>
        )}
        <div className="year-navigation">
          <button
            aria-label="前年"
            onClick={() => setYear((value) => value - 1)}
          >
            <ChevronLeft />
          </button>
          <strong>
            <CalendarDays size={20} />
            {year}年
          </strong>
          <button
            aria-label="翌年"
            onClick={() => setYear((value) => value + 1)}
          >
            <ChevronRight />
          </button>
        </div>
      </section>

      {error && <div className="error">{error}</div>}
      {loading ? (
        <div className="summary-empty">読み込み中...</div>
      ) : (
        <>
          <section className="selected-month-summary">
            <button
              aria-label="前月"
              disabled={selectedMonth === 1}
              onClick={() => setSelectedMonth((value) => value - 1)}
            >
              <ChevronLeft />
            </button>
            <div>
              <span>{selected.month}月</span>
              <strong
                className={
                  selected.balance >= 0 ? "income-amount" : "expense-amount"
                }
              >
                収支 {signedMoney(selected.balance)}
              </strong>
              <small>
                収入 {money.format(selected.income)}・支出{" "}
                {money.format(selected.expense)}
              </small>
            </div>
            <button
              aria-label="翌月"
              disabled={selectedMonth === 12}
              onClick={() => setSelectedMonth((value) => value + 1)}
            >
              <ChevronRight />
            </button>
          </section>

          <GraphCard
            title="支出"
            color="expense"
            points={points.map((point) => point.expense)}
            selectedMonth={selectedMonth}
            onSelect={setSelectedMonth}
          />
          <GraphCard
            title="収入"
            color="income"
            points={points.map((point) => point.income)}
            selectedMonth={selectedMonth}
            onSelect={setSelectedMonth}
          />
          <GraphCard
            title="収支"
            color="balance"
            points={points.map((point) => point.balance)}
            selectedMonth={selectedMonth}
            onSelect={setSelectedMonth}
          />
        </>
      )}
    </main>
  );
}

function GraphCard({
  title,
  color,
  points,
  selectedMonth,
  onSelect,
}: {
  title: string;
  color: "expense" | "income" | "balance";
  points: number[];
  selectedMonth: number;
  onSelect: (month: number) => void;
}) {
  const maximum = Math.max(...points.map(Math.abs), 1);
  return (
    <section className="graph-card">
      <h2>{title}</h2>
      <div className="bar-chart">
        {points.map((value, index) => (
          <button
            className={`${color} ${selectedMonth === index + 1 ? "selected" : ""}`}
            key={index}
            title={`${index + 1}月 ${money.format(value)}`}
            onClick={() => onSelect(index + 1)}
          >
            <span
              style={{
                height: `${Math.max((Math.abs(value) / maximum) * 100, value ? 3 : 0)}%`,
              }}
            />
            <small>{index + 1}</small>
          </button>
        ))}
      </div>
    </section>
  );
}

function signedMoney(value: number) {
  if (value === 0) return money.format(0);
  return `${value > 0 ? "+" : "-"}${money.format(Math.abs(value))}`;
}
