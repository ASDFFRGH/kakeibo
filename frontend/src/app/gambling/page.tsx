"use client";

import {
  FormEvent,
  Suspense,
  useCallback,
  useEffect,
  useMemo,
  useState,
} from "react";
import {
  CalendarDays,
  ChevronLeft,
  ChevronRight,
  Dices,
  BarChart3,
  Plus,
  RefreshCw,
  RotateCcw,
  Trash2,
  X,
} from "lucide-react";
import { useSearchParams } from "next/navigation";
import Link from "next/link";
import { LedgerNavigation } from "../components/LedgerNavigation";
import { api, GamblingRecord } from "../../lib/api";
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

const gameTypes = [
  "競馬",
  "競輪",
  "競艇",
  "オートレース",
  "パチンコ",
  "パチスロ",
  "スポーツベッティング",
  "FX",
  "株",
  "その他",
];

export default function GamblingRoute() {
  return (
    <Suspense fallback={<main className="gambling-page" />}>
      <GamblingPage />
    </Suspense>
  );
}

function GamblingPage() {
  const searchParams = useSearchParams();
  const [month, setMonth] = useState(() =>
    resolveMonth(searchParams.get("month"), localToday()),
  );
  const [selectedDate, setSelectedDate] = useState(() =>
    searchParams.has("month") ? `${month}-01` : localToday(),
  );
  const [records, setRecords] = useState<GamblingRecord[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [dialog, setDialog] = useState<"record" | "trash" | null>(null);
  const [editing, setEditing] = useState<GamblingRecord | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const { from, to } = gamblingMonthRange(month);
      const query = `?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`;
      setRecords(await api.gamblingRecords(query));
    } catch (reason) {
      setError(
        reason instanceof Error ? reason.message : "記録の読込に失敗しました",
      );
    } finally {
      setLoading(false);
    }
  }, [month]);

  useEffect(() => {
    void load();
  }, [load]);

  const totals = useMemo(() => gamblingTotals(records), [records]);
  const selectedRecords = useMemo(
    () => records.filter((record) => record.date === selectedDate),
    [records, selectedDate],
  );
  const dailyTotals = useMemo(() => {
    const totalsByDate = new Map<string, { stake: number; payout: number }>();
    records.forEach((record) => {
      const total = totalsByDate.get(record.date) ?? { stake: 0, payout: 0 };
      total.stake += record.stake_amount;
      total.payout += record.payout_amount;
      totalsByDate.set(record.date, total);
    });
    return totalsByDate;
  }, [records]);
  const calendarDays = useMemo(() => buildCalendarDays(month), [month]);

  const shift = (amount: number) => {
    const nextMonth = shiftGamblingMonth(month, amount);
    const currentDay = Number(selectedDate.slice(8, 10));
    const nextLastDay = Number(gamblingMonthRange(nextMonth).to.slice(8, 10));
    setMonth(nextMonth);
    setSelectedDate(
      `${nextMonth}-${String(Math.min(currentDay, nextLastDay)).padStart(2, "0")}`,
    );
  };

  const startAdding = () => {
    setEditing(null);
    setMessage("");
    setDialog("record");
  };

  const startEditing = (record: GamblingRecord) => {
    setEditing(record);
    setMessage("");
    setDialog("record");
  };

  const remove = async (record: GamblingRecord) => {
    if (
      !window.confirm(
        `${formatDate(record.date)}の${record.game_type}をゴミ箱へ移動しますか？`,
      )
    ) {
      return;
    }
    setError("");
    setMessage("");
    try {
      await api.deleteGamblingRecord(record.uuid);
      setMessage("記録をゴミ箱へ移動しました。");
      await load();
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "削除に失敗しました");
    }
  };

  return (
    <main className="gambling-page">
      <header className="gambling-header">
        <div className="brand">
          <Dices />
          <span>ギャンブル収支</span>
        </div>
        <div className="gambling-header-actions">
          <Link
            className="gambling-summary-link"
            href={`/gambling/summary?scope=month&month=${encodeURIComponent(month)}`}
          >
            <BarChart3 size={18} />
            集計
          </Link>
          <button
            className="gambling-icon-button"
            type="button"
            aria-label="ギャンブル収支のゴミ箱を開く"
            title="ゴミ箱"
            onClick={() => setDialog("trash")}
          >
            <Trash2 size={18} />
          </button>
          <button
            className="gambling-icon-button"
            type="button"
            aria-label="記録を再読み込み"
            title="再読み込み"
            disabled={loading}
            onClick={() => void load()}
          >
            <RefreshCw size={18} />
          </button>
          <button className="primary gambling-add-button" onClick={startAdding}>
            <Plus size={18} />
            記録を追加
          </button>
        </div>
      </header>

      <LedgerNavigation active="gambling" month={month} />

      {error && (
        <div className="error" role="alert">
          {error}
        </div>
      )}
      {message && (
        <div className="gambling-success" role="status">
          {message}
        </div>
      )}

      <div className="calendar-dashboard gambling-calendar-dashboard">
        <div className="calendar-panel">
          <section className="calendar-toolbar">
            <button aria-label="前月" onClick={() => shift(-1)}>
              <ChevronLeft />
            </button>
            <strong>
              <CalendarDays size={23} />
              {formatMonth(month)}
            </strong>
            <button aria-label="翌月" onClick={() => shift(1)}>
              <ChevronRight />
            </button>
          </section>
          <section
            className="calendar-card"
            aria-label={`${formatMonth(month)}のカレンダー`}
          >
            <div className="weekday-row" aria-hidden="true">
              {["日", "月", "火", "水", "木", "金", "土"].map((day, index) => (
                <span
                  className={
                    index === 0 ? "sunday" : index === 6 ? "saturday" : ""
                  }
                  key={day}
                >
                  {day}
                </span>
              ))}
            </div>
            <div className="calendar-grid">
              {calendarDays.map((date, index) =>
                date ? (
                  <button
                    className={date === selectedDate ? "selected" : ""}
                    key={date}
                    onClick={() => setSelectedDate(date)}
                  >
                    <span>{Number(date.slice(8, 10))}</span>
                    {dailyTotals.has(date) && (
                      <span className="gambling-calendar-day-totals">
                        <small className="expense-amount">
                          投 {money.format(dailyTotals.get(date)?.stake ?? 0)}
                        </small>
                        <small className="income-amount">
                          回 {money.format(dailyTotals.get(date)?.payout ?? 0)}
                        </small>
                      </span>
                    )}
                  </button>
                ) : (
                  <span className="calendar-blank" key={`blank-${index}`} />
                ),
              )}
            </div>
          </section>
          <div className="calendar-balance">
            <span>
              <small>投資額</small>
              <strong className="expense-amount">
                {money.format(totals.stake)}
              </strong>
            </span>
            <span>
              <small>回収額</small>
              <strong className="income-amount">
                {money.format(totals.payout)}
              </strong>
            </span>
            <span>
              <small>収支（{totals.count}件）</small>
              <strong className={balanceClass(totals.balance)}>
                {signedMoney(totals.balance)}
              </strong>
            </span>
          </div>
        </div>
        <section
          className="selected-day gambling-selected-day"
          aria-labelledby="gambling-selected-date"
        >
          <div className="selected-day-heading">
            <span>選択日の記録</span>
            <h2 id="gambling-selected-date">{formatDate(selectedDate)}</h2>
          </div>
          {loading ? (
            <div className="day-empty">読み込み中...</div>
          ) : selectedRecords.length === 0 ? (
            <div className="day-empty">この日の記録はありません</div>
          ) : (
            <div className="transaction-cards">
              {selectedRecords.map((record) => {
                const balance = record.payout_amount - record.stake_amount;
                return (
                  <article
                    className="transaction-card gambling-transaction-card"
                    key={record.uuid}
                  >
                    <button
                      className="transaction-card-main"
                      onClick={() => startEditing(record)}
                    >
                      <span>
                        <strong>{record.game_type}</strong>
                        {record.memo && <small>{record.memo}</small>}
                      </span>
                      <b className={balanceClass(balance)}>
                        {signedMoney(balance)}
                      </b>
                      <ChevronRight />
                    </button>
                    <button
                      className="transaction-delete"
                      aria-label={`${formatDate(record.date)}の${record.game_type}を削除`}
                      title="削除"
                      onClick={() => void remove(record)}
                    >
                      <Trash2 size={17} />
                    </button>
                  </article>
                );
              })}
            </div>
          )}
        </section>
      </div>

      <button className="gambling-fab" onClick={startAdding}>
        <Plus />
        記録を追加
      </button>

      {dialog === "record" && (
        <GamblingRecordDialog
          month={month}
          selectedDate={selectedDate}
          record={editing}
          close={() => {
            setDialog(null);
            setEditing(null);
          }}
          saved={async (savedDate) => {
            setDialog(null);
            setEditing(null);
            setSelectedDate(savedDate);
            setMessage(
              editing ? "記録を更新しました。" : "記録を追加しました。",
            );
            await load();
          }}
        />
      )}
      {dialog === "trash" && (
        <GamblingTrashDialog
          close={() => setDialog(null)}
          changed={async () => {
            setMessage("記録を復元しました。");
            await load();
          }}
        />
      )}
    </main>
  );
}

function GamblingRecordDialog({
  month,
  selectedDate,
  record,
  close,
  saved,
}: {
  month: string;
  selectedDate: string;
  record: GamblingRecord | null;
  close: () => void;
  saved: (date: string) => Promise<void>;
}) {
  const [date, setDate] = useState(record?.date ?? selectedDate);
  const [gameType, setGameType] = useState(record?.game_type ?? "");
  const [stakeAmount, setStakeAmount] = useState(
    record?.stake_amount.toString() ?? "0",
  );
  const [payoutAmount, setPayoutAmount] = useState(
    record?.payout_amount.toString() ?? "0",
  );
  const [memo, setMemo] = useState(record?.memo ?? "");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const stake = Number(stakeAmount || 0);
  const payout = Number(payoutAmount || 0);
  const balance = payout - stake;
  const { from, to } = gamblingMonthRange(month);

  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape" && !busy) close();
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [busy, close]);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setError("");
    if (!Number.isSafeInteger(stake) || !Number.isSafeInteger(payout)) {
      setError("金額は整数で入力してください。");
      return;
    }
    if (stake === 0 && payout === 0) {
      setError("投資額か回収額のどちらかを入力してください。");
      return;
    }
    setBusy(true);
    try {
      await api.saveGamblingRecord(
        {
          uuid: record?.uuid ?? crypto.randomUUID(),
          date,
          stake_amount: stake,
          payout_amount: payout,
          game_type: gameType.trim(),
          memo: memo.trim(),
        },
        record !== null,
      );
      await saved(date);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "保存に失敗しました");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div
      className="overlay"
      onMouseDown={(event) =>
        event.target === event.currentTarget && !busy && close()
      }
    >
      <form
        className="dialog gambling-record-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="gambling-dialog-title"
        onSubmit={submit}
      >
        <div className="dialog-title">
          <h2 id="gambling-dialog-title">
            {record ? "記録を編集" : "記録を追加"}
          </h2>
          <button
            type="button"
            aria-label="閉じる"
            disabled={busy}
            onClick={close}
          >
            <X />
          </button>
        </div>

        <label>
          日付
          <input
            autoFocus
            required
            type="date"
            min={from}
            max={to}
            value={date}
            onChange={(event) => setDate(event.target.value)}
          />
        </label>
        <label>
          種目
          <input
            required
            maxLength={80}
            list="gambling-game-types"
            value={gameType}
            onChange={(event) => setGameType(event.target.value)}
            placeholder="例：競馬"
          />
          <datalist id="gambling-game-types">
            {gameTypes.map((game) => (
              <option value={game} key={game} />
            ))}
          </datalist>
        </label>
        <div className="gambling-amount-fields">
          <label>
            投資額
            <span className="gambling-yen-input">
              <span aria-hidden="true">¥</span>
              <input
                required
                inputMode="numeric"
                min="0"
                step="1"
                type="number"
                value={stakeAmount}
                onChange={(event) => setStakeAmount(event.target.value)}
              />
            </span>
          </label>
          <label>
            回収額
            <span className="gambling-yen-input">
              <span aria-hidden="true">¥</span>
              <input
                required
                inputMode="numeric"
                min="0"
                step="1"
                type="number"
                value={payoutAmount}
                onChange={(event) => setPayoutAmount(event.target.value)}
              />
            </span>
          </label>
        </div>
        <div className="gambling-balance-preview" aria-live="polite">
          <span>この記録の収支</span>
          <strong className={balanceClass(balance)}>
            {signedMoney(balance)}
          </strong>
        </div>
        <label>
          メモ <small>任意</small>
          <input
            maxLength={500}
            value={memo}
            onChange={(event) => setMemo(event.target.value)}
            placeholder="例：東京11R"
          />
        </label>

        {error && (
          <div className="field-error gambling-form-error" role="alert">
            {error}
          </div>
        )}
        <div className="dialog-actions">
          <button
            type="button"
            className="secondary"
            disabled={busy}
            onClick={close}
          >
            キャンセル
          </button>
          <button className="primary" disabled={busy}>
            {busy ? "保存中..." : record ? "更新" : "追加"}
          </button>
        </div>
      </form>
    </div>
  );
}

function GamblingTrashDialog({
  close,
  changed,
}: {
  close: () => void;
  changed: () => Promise<void>;
}) {
  const [records, setRecords] = useState<GamblingRecord[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [restoringId, setRestoringId] = useState("");

  const loadTrash = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      setRecords(await api.gamblingTrash());
    } catch (reason) {
      setError(
        reason instanceof Error ? reason.message : "ゴミ箱の読込に失敗しました",
      );
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadTrash();
  }, [loadTrash]);

  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape" && !restoringId) close();
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [close, restoringId]);

  const restore = async (record: GamblingRecord) => {
    setRestoringId(record.uuid);
    setError("");
    try {
      await api.restoreGamblingRecord(record.uuid);
      await Promise.all([loadTrash(), changed()]);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "復元に失敗しました");
    } finally {
      setRestoringId("");
    }
  };

  return (
    <div
      className="overlay"
      onMouseDown={(event) =>
        event.target === event.currentTarget && !restoringId && close()
      }
    >
      <section
        className="dialog gambling-trash-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="gambling-trash-title"
      >
        <div className="dialog-title">
          <h2 id="gambling-trash-title">ギャンブル収支のゴミ箱</h2>
          <button
            type="button"
            aria-label="閉じる"
            disabled={!!restoringId}
            onClick={close}
          >
            <X />
          </button>
        </div>
        <p>削除した記録を元に戻せます。</p>
        {error && (
          <div className="error" role="alert">
            {error}
          </div>
        )}
        {loading ? (
          <div className="gambling-trash-empty">読み込み中...</div>
        ) : records.length === 0 ? (
          <div className="gambling-trash-empty">ゴミ箱は空です</div>
        ) : (
          <div className="gambling-trash-list">
            {[...records]
              .sort((left, right) => right.date.localeCompare(left.date))
              .map((record) => (
                <article key={record.uuid}>
                  <div>
                    <time dateTime={record.date}>
                      {formatDate(record.date)}
                    </time>
                    <strong>{record.game_type}</strong>
                    <small>
                      投資 {money.format(record.stake_amount)}・回収{" "}
                      {money.format(record.payout_amount)}
                    </small>
                  </div>
                  <button
                    className="secondary"
                    type="button"
                    disabled={!!restoringId}
                    onClick={() => void restore(record)}
                  >
                    <RotateCcw size={16} />
                    {restoringId === record.uuid ? "復元中..." : "復元"}
                  </button>
                </article>
              ))}
          </div>
        )}
      </section>
    </div>
  );
}

function formatMonth(month: string) {
  const [year, monthNumber] = month.split("-");
  return `${year}年${Number(monthNumber)}月`;
}

function buildCalendarDays(month: string): (string | null)[] {
  const [year, monthNumber] = month.split("-").map(Number);
  const firstWeekday = new Date(year, monthNumber - 1, 1).getDay();
  const lastDay = Number(gamblingMonthRange(month).to.slice(8, 10));
  return [
    ...Array<null>(firstWeekday).fill(null),
    ...Array.from(
      { length: lastDay },
      (_, index) => `${month}-${String(index + 1).padStart(2, "0")}`,
    ),
  ];
}

function formatDate(date: string) {
  return new Date(`${date}T00:00:00`).toLocaleDateString("ja-JP", {
    month: "numeric",
    day: "numeric",
    weekday: "short",
  });
}

function balanceClass(value: number) {
  return value >= 0 ? "income-amount" : "expense-amount";
}

function signedMoney(value: number) {
  if (value === 0) return money.format(0);
  return `${value > 0 ? "+" : "-"}${money.format(Math.abs(value))}`;
}
