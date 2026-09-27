"use client";

import Link from "next/link";
import {
  CalendarDays,
  ChevronLeft,
  ChevronRight,
  RefreshCw,
  Scale,
} from "lucide-react";
import { Suspense, useCallback, useEffect, useMemo, useState } from "react";
import { useSearchParams } from "next/navigation";
import { LedgerNavigation } from "../components/LedgerNavigation";
import { api, GamblingRecord, Summary, SummaryPeriod } from "../../lib/api";
import { localToday } from "../../lib/calendar-date.mjs";
import {
  gamblingMonthRange,
  gamblingTotals,
  shiftGamblingMonth,
} from "../../lib/gambling-records.mjs";
import { resolveMonth } from "../../lib/summary-period.mjs";

const money = new Intl.NumberFormat("ja-JP", {
  style: "currency",
  currency: "JPY",
  maximumFractionDigits: 0,
});

export default function CombinedSummaryRoute() {
  return (
    <Suspense fallback={<main className="combined-summary-page" />}>
      <CombinedSummaryPage />
    </Suspense>
  );
}

function CombinedSummaryPage() {
  const searchParams = useSearchParams();
  const [month, setMonth] = useState(() =>
    resolveMonth(searchParams.get("month"), localToday()),
  );
  const [period, setPeriod] = useState<
    Extract<SummaryPeriod, "month" | "year">
  >(() => (searchParams.get("period") === "year" ? "year" : "month"));
  const [household, setHousehold] = useState<Summary | null>(null);
  const [records, setRecords] = useState<GamblingRecord[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const year = month.slice(0, 4);
      const { from, to } =
        period === "month"
          ? gamblingMonthRange(month)
          : { from: `${year}-01-01`, to: `${year}-12-31` };
      const [summary, gambling] = await Promise.all([
        api.summary(period, period === "month" ? `${month}-01` : from),
        api.gamblingRecords(
          `?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`,
        ),
      ]);
      setHousehold(summary);
      setRecords(gambling);
    } catch (reason) {
      setError(
        reason instanceof Error ? reason.message : "集計の読込に失敗しました",
      );
    } finally {
      setLoading(false);
    }
  }, [month, period]);

  useEffect(() => {
    void load();
  }, [load]);
  const gambling = useMemo(() => gamblingTotals(records), [records]);
  const householdBalance = household?.balance ?? 0;
  const totalBalance = householdBalance + gambling.balance;
  const totalIncome = (household?.total_income ?? 0) + gambling.payout;
  const totalExpense = (household?.total_expense ?? 0) + gambling.stake;
  const shift = (amount: number) => {
    if (period === "month") {
      setMonth(shiftGamblingMonth(month, amount));
      return;
    }
    setMonth(`${Number(month.slice(0, 4)) + amount}${month.slice(4)}`);
  };
  const periodLabel =
    period === "month" ? formatMonth(month) : `${month.slice(0, 4)}年`;

  return (
    <main className="combined-summary-page">
      <header className="gambling-header">
        <div className="brand">
          <Scale />
          <span>全体サマリー</span>
        </div>
        <button
          className="gambling-icon-button"
          type="button"
          aria-label="集計を再読み込み"
          title="再読み込み"
          disabled={loading}
          onClick={() => void load()}
        >
          <RefreshCw size={18} />
        </button>
      </header>
      <LedgerNavigation active="combined" month={month} />
      <section className="combined-summary-heading">
        <div>
          <span>家計簿とギャンブル収支を合算</span>
          <h1>全体の収支</h1>
        </div>
        <div className="combined-period-controls" aria-label="集計期間">
          <div className="summary-tabs">
            <button
              className={period === "month" ? "active" : ""}
              onClick={() => setPeriod("month")}
            >
              月別
            </button>
            <button
              className={period === "year" ? "active" : ""}
              onClick={() => setPeriod("year")}
            >
              年別
            </button>
          </div>
          <div className="gambling-month-navigation">
            <button
              aria-label={period === "month" ? "前月" : "前年"}
              onClick={() => shift(-1)}
            >
              <ChevronLeft />
            </button>
            <label>
              <CalendarDays size={19} />
              <span className="sr-only">
                対象{period === "month" ? "月" : "年"}
              </span>
              {period === "month" ? (
                <input
                  aria-label="対象月"
                  type="month"
                  value={month}
                  onChange={(event) =>
                    event.target.value && setMonth(event.target.value)
                  }
                />
              ) : (
                <span className="combined-year-label">
                  {month.slice(0, 4)}年
                </span>
              )}
            </label>
            <button
              aria-label={period === "month" ? "翌月" : "翌年"}
              onClick={() => shift(1)}
            >
              <ChevronRight />
            </button>
          </div>
        </div>
      </section>
      {error ? (
        <div className="error combined-summary-error" role="alert">
          {error}
        </div>
      ) : loading ? (
        <section className="summary-empty">集計を読み込んでいます...</section>
      ) : (
        <>
          <section
            className="combined-total"
            aria-label={`${periodLabel}の合計収支`}
          >
            <span>{periodLabel}の合計収支</span>
            <strong className={amountClass(totalBalance)}>
              {signedMoney(totalBalance)}
            </strong>
            <div>
              <span className="income-amount">
                入金 {money.format(totalIncome)}
              </span>
              <span className="expense-amount">
                出金 {money.format(totalExpense)}
              </span>
            </div>
          </section>
          <section className="combined-ledgers" aria-label="台帳別の内訳">
            <article>
              <div>
                <h2>家計簿</h2>
                <Link href={`/summary?month=${month}`}>家計簿サマリーへ</Link>
              </div>
              <dl>
                <div>
                  <dt>収入</dt>
                  <dd className="income-amount">
                    {money.format(household?.total_income ?? 0)}
                  </dd>
                </div>
                <div>
                  <dt>支出</dt>
                  <dd className="expense-amount">
                    {money.format(household?.total_expense ?? 0)}
                  </dd>
                </div>
                <div>
                  <dt>収支</dt>
                  <dd className={amountClass(householdBalance)}>
                    {signedMoney(householdBalance)}
                  </dd>
                </div>
              </dl>
            </article>
            <article>
              <div>
                <h2>ギャンブル</h2>
                <Link
                  href={`/gambling/summary?scope=${period === "month" ? "month" : "all"}&month=${month}`}
                >
                  {period === "month" ? "ギャンブル集計へ" : "全期間の集計へ"}
                </Link>
              </div>
              <dl>
                <div>
                  <dt>投資額</dt>
                  <dd className="expense-amount">
                    {money.format(gambling.stake)}
                  </dd>
                </div>
                <div>
                  <dt>回収額</dt>
                  <dd className="income-amount">
                    {money.format(gambling.payout)}
                  </dd>
                </div>
                <div>
                  <dt>収支</dt>
                  <dd className={amountClass(gambling.balance)}>
                    {signedMoney(gambling.balance)}
                  </dd>
                </div>
              </dl>
            </article>
          </section>
        </>
      )}
    </main>
  );
}

function formatMonth(month: string) {
  const [year, monthNumber] = month.split("-");
  return `${year}年${Number(monthNumber)}月`;
}
function amountClass(value: number) {
  return value >= 0 ? "income-amount" : "expense-amount";
}
function signedMoney(value: number) {
  return value === 0
    ? money.format(0)
    : `${value > 0 ? "+" : "-"}${money.format(Math.abs(value))}`;
}
