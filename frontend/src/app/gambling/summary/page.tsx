"use client";

import Link from "next/link";
import {
  BarChart3,
  ChevronLeft,
  ChevronRight,
  Dices,
  RefreshCw,
} from "lucide-react";
import { Suspense, useCallback, useEffect, useMemo, useState } from "react";
import { useSearchParams } from "next/navigation";
import { LedgerNavigation } from "../../components/LedgerNavigation";
import { api, GamblingRecord } from "../../../lib/api";
import { localToday } from "../../../lib/calendar-date.mjs";
import {
  gamblingRecordsForMonth,
  gamblingTotals,
  gamblingTotalsByGameType,
  gamblingTotalsByMonth,
  shiftGamblingMonth,
} from "../../../lib/gambling-records.mjs";
import { resolveMonth } from "../../../lib/summary-period.mjs";

const money = new Intl.NumberFormat("ja-JP", {
  style: "currency",
  currency: "JPY",
  maximumFractionDigits: 0,
});

export default function GamblingSummaryRoute() {
  return (
    <Suspense fallback={<main className="gambling-page" />}>
      <GamblingSummaryPage />
    </Suspense>
  );
}

function GamblingSummaryPage() {
  const searchParams = useSearchParams();
  const scope = searchParams.get("scope") === "all" ? "all" : "month";
  const [month, setMonth] = useState(() =>
    resolveMonth(searchParams.get("month"), localToday()),
  );
  const [records, setRecords] = useState<GamblingRecord[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      setRecords(await api.gamblingRecords());
    } catch (reason) {
      setError(
        reason instanceof Error ? reason.message : "集計の読込に失敗しました",
      );
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const selectedRecords = useMemo(
    () =>
      scope === "month" ? gamblingRecordsForMonth(records, month) : records,
    [month, records, scope],
  );
  const totals = useMemo(
    () => gamblingTotals(selectedRecords),
    [selectedRecords],
  );
  const gameTypes = useMemo(
    () => gamblingTotalsByGameType(selectedRecords),
    [selectedRecords],
  );
  const months = useMemo(() => gamblingTotalsByMonth(records), [records]);
  const monthQuery = encodeURIComponent(month);

  return (
    <main className="gambling-page gambling-summary-page">
      <header className="gambling-header">
        <div className="brand">
          <Dices />
          <span>ギャンブル収支</span>
        </div>
        <div className="gambling-header-actions">
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
          <Link
            className="secondary gambling-back-link"
            href={`/gambling?month=${monthQuery}`}
          >
            記録一覧へ
          </Link>
        </div>
      </header>

      <LedgerNavigation active="gambling" month={month} />

      <section className="gambling-heading gambling-summary-heading">
        <div>
          <span>記録をまとめて確認</span>
          <h1>収支集計</h1>
          <p>投資額、回収額、収支を期間や種目ごとに確認できます。</p>
        </div>
        {scope === "month" && <MonthPicker month={month} setMonth={setMonth} />}
      </section>

      <nav className="gambling-summary-scopes" aria-label="集計期間">
        <Link
          className={scope === "month" ? "active" : ""}
          aria-current={scope === "month" ? "page" : undefined}
          href={`/gambling/summary?scope=month&month=${monthQuery}`}
        >
          月別
        </Link>
        <Link
          className={scope === "all" ? "active" : ""}
          aria-current={scope === "all" ? "page" : undefined}
          href={`/gambling/summary?scope=all&month=${monthQuery}`}
        >
          全体
        </Link>
      </nav>

      {error && (
        <div className="error gambling-summary-error" role="alert">
          <span>{error}</span>
          <button
            className="secondary"
            type="button"
            onClick={() => void load()}
          >
            再試行
          </button>
        </div>
      )}

      {loading ? (
        <section className="gambling-summary-state" aria-live="polite">
          集計を読み込んでいます...
        </section>
      ) : error ? null : records.length === 0 ? (
        <section className="gambling-summary-state">
          <Dices size={36} aria-hidden="true" />
          <strong>集計できる記録はありません</strong>
          <span>記録を追加すると、ここで収支の傾向を確認できます。</span>
          <Link className="primary" href={`/gambling?month=${monthQuery}`}>
            記録を追加
          </Link>
        </section>
      ) : (
        <>
          <section
            className="gambling-totals"
            aria-label={
              scope === "month"
                ? `${formatMonth(month)}の集計`
                : "これまでの集計"
            }
          >
            <article>
              <span>投資額</span>
              <strong className="expense-amount">
                {money.format(totals.stake)}
              </strong>
            </article>
            <article>
              <span>回収額</span>
              <strong className="income-amount">
                {money.format(totals.payout)}
              </strong>
            </article>
            <article className="gambling-balance-card">
              <span>収支</span>
              <strong className={balanceClass(totals.balance)}>
                {signedMoney(totals.balance)}
              </strong>
              <small>{totals.count}件</small>
            </article>
          </section>

          {scope === "month" &&
            (selectedRecords.length === 0 ? (
              <section className="gambling-summary-state gambling-summary-no-records">
                <strong>{formatMonth(month)}の記録はありません</strong>
                <Link
                  className="secondary"
                  href={`/gambling?month=${monthQuery}`}
                >
                  記録一覧へ
                </Link>
              </section>
            ) : (
              <SummaryTable
                title="種目別の内訳"
                label="種目"
                rows={gameTypes}
              />
            ))}
          {scope === "all" && (
            <SummaryTable
              title="月別の内訳"
              label="月"
              rows={months.map((row) => ({
                ...row,
                label: formatMonth(row.month),
              }))}
            />
          )}
        </>
      )}
    </main>
  );
}

function MonthPicker({
  month,
  setMonth,
}: {
  month: string;
  setMonth: (month: string) => void;
}) {
  return (
    <div className="gambling-month-navigation">
      <button
        type="button"
        aria-label="前月"
        onClick={() => setMonth(shiftGamblingMonth(month, -1))}
      >
        <ChevronLeft />
      </button>
      <label>
        <span className="sr-only">対象月</span>
        <input
          aria-label="対象月"
          type="month"
          value={month}
          onChange={(event) =>
            event.target.value && setMonth(event.target.value)
          }
        />
      </label>
      <button
        type="button"
        aria-label="翌月"
        onClick={() => setMonth(shiftGamblingMonth(month, 1))}
      >
        <ChevronRight />
      </button>
    </div>
  );
}

function SummaryTable({
  title,
  label,
  rows,
}: {
  title: string;
  label: string;
  rows: Array<{
    stake: number;
    payout: number;
    balance: number;
    count: number;
    game_type?: string;
    label?: string;
  }>;
}) {
  return (
    <section
      className="gambling-summary-table"
      aria-labelledby={`${title}-title`}
    >
      <div className="gambling-records-title">
        <div>
          <BarChart3 size={17} aria-hidden="true" />
          <h2 id={`${title}-title`}>{title}</h2>
        </div>
        <small>{rows.length}件</small>
      </div>
      <div className="gambling-summary-table-head" aria-hidden="true">
        <span>{label}</span>
        <span>投資額</span>
        <span>回収額</span>
        <span>収支</span>
        <span>件数</span>
      </div>
      {rows.map((row) => (
        <article
          className="gambling-summary-row"
          key={row.game_type ?? row.label}
        >
          <strong>{row.label ?? row.game_type}</strong>
          <span className="gambling-stake">
            <small>投資額</small>
            {money.format(row.stake)}
          </span>
          <span className="gambling-payout">
            <small>回収額</small>
            {money.format(row.payout)}
          </span>
          <strong className={balanceClass(row.balance)}>
            <small>収支</small>
            {signedMoney(row.balance)}
          </strong>
          <span>
            <small>件数</small>
            {row.count}件
          </span>
        </article>
      ))}
    </section>
  );
}

function formatMonth(month: string) {
  const [year, monthNumber] = month.split("-");
  return `${year}年${Number(monthNumber)}月`;
}
function balanceClass(value: number) {
  return value >= 0 ? "income-amount" : "expense-amount";
}
function signedMoney(value: number) {
  return value === 0
    ? money.format(0)
    : `${value > 0 ? "+" : "-"}${money.format(Math.abs(value))}`;
}
