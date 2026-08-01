"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import {
  ArrowLeft,
  CalendarDays,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  CircleDollarSign,
  Eraser,
  Info,
  List,
  ReceiptText,
  WalletCards,
  X,
} from "lucide-react";
import { api, Category, Expense } from "../../lib/api";

type TransactionType = Expense["type"];
type Amounts = Record<string, string>;

const money = new Intl.NumberFormat("ja-JP", {
  style: "currency",
  currency: "JPY",
  maximumFractionDigits: 0,
});
const currentMonth = () => new Date().toISOString().slice(0, 7);

export default function MonthlyEntryPage() {
  const [month, setMonth] = useState(currentMonth());
  const [date, setDate] = useState(monthEnd(currentMonth()));
  const [type, setType] = useState<TransactionType>("expense");
  const [categories, setCategories] = useState<Category[]>([]);
  const [expenses, setExpenses] = useState<Expense[]>([]);
  const [amounts, setAmounts] = useState<Amounts>({});
  const [batchUuids, setBatchUuids] = useState<Record<string, string>>({});
  const [memo, setMemo] = useState("月次まとめ");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [saveError, setSaveError] = useState("");
  const [saving, setSaving] = useState(false);
  const [reviewOpen, setReviewOpen] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const [loadedCategories, loadedExpenses] = await Promise.all([
        api.categories(),
        api.expenses(`?from=${month}-01&to=${monthEnd(month)}`),
      ]);
      setCategories(loadedCategories);
      setExpenses(loadedExpenses);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "読込に失敗しました");
    } finally {
      setLoading(false);
    }
  }, [month]);

  useEffect(() => {
    void load();
  }, [load]);

  const visibleCategories = useMemo(
    () =>
      categories.filter((category) => (category.type || "expense") === type),
    [categories, type],
  );
  const existingByCategory = useMemo(() => {
    const result = new Map<string, { amount: number; count: number }>();
    expenses
      .filter((expense) => expense.type === type)
      .forEach((expense) => {
        const current = result.get(expense.category_uuid) ?? {
          amount: 0,
          count: 0,
        };
        result.set(expense.category_uuid, {
          amount: current.amount + expense.amount,
          count: current.count + 1,
        });
      });
    return result;
  }, [expenses, type]);
  const entries = useMemo(
    () =>
      visibleCategories
        .map((category) => ({
          category,
          amount: Number(amounts[category.uuid] ?? 0),
        }))
        .filter((entry) => entry.amount > 0),
    [amounts, visibleCategories],
  );
  const inputTotal = entries.reduce((sum, entry) => sum + entry.amount, 0);
  const existingTotal = [...existingByCategory.values()].reduce(
    (sum, item) => sum + item.amount,
    0,
  );
  const isDirty = entries.length > 0;

  const changeContext = (
    nextMonth: string,
    nextType: TransactionType = type,
  ) => {
    if (
      isDirty &&
      !window.confirm("入力中の金額を破棄して表示を切り替えますか？")
    ) {
      return;
    }
    setAmounts({});
    setMonth(nextMonth);
    setDate(monthEnd(nextMonth));
    setType(nextType);
  };
  const shiftMonth = (delta: number) => {
    const [year, monthNumber] = month.split("-").map(Number);
    const shifted = new Date(year, monthNumber - 1 + delta, 1);
    changeContext(
      `${shifted.getFullYear()}-${String(shifted.getMonth() + 1).padStart(2, "0")}`,
    );
  };
  const changeType = (nextType: TransactionType) => {
    if (nextType === type) return;
    changeContext(month, nextType);
  };
  const updateAmount = (categoryUuid: string, value: string) => {
    const digits = value.replace(/\D/g, "");
    setAmounts((current) => ({ ...current, [categoryUuid]: digits }));
  };
  const clearAmounts = () => {
    if (!isDirty || window.confirm("入力した金額をすべてクリアしますか？")) {
      setAmounts({});
    }
  };
  const saveEntries = async () => {
    setSaving(true);
    setSaveError("");
    setSuccess("");
    try {
      const saved = await api.saveExpenses(
        entries.map((entry) => ({
          uuid: batchUuids[entry.category.uuid],
          date,
          amount: entry.amount,
          type,
          category_uuid: entry.category.uuid,
          memo,
        })),
      );
      setReviewOpen(false);
      setAmounts({});
      setBatchUuids({});
      setSuccess(`${saved.length}件の${typeLabel(type)}を登録しました。`);
      await load();
    } catch (reason) {
      setSaveError(
        reason instanceof Error ? reason.message : "一括登録に失敗しました",
      );
    } finally {
      setSaving(false);
    }
  };
  const openReview = () => {
    setSaveError("");
    setBatchUuids(
      Object.fromEntries(
        entries.map((entry) => [entry.category.uuid, crypto.randomUUID()]),
      ),
    );
    setReviewOpen(true);
  };

  return (
    <main className="batch-entry-page">
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

      <section className="batch-heading">
        <div>
          <p>月次入力</p>
          <h1>カテゴリごとにまとめて入力</h1>
          <small>1か月分の収支を、カテゴリごとに1件ずつ追加します。</small>
        </div>
        <Link className="batch-back-link" href="/">
          <ArrowLeft size={17} />
          一覧へ戻る
        </Link>
      </section>

      <section className="batch-context-card" aria-label="入力対象">
        <div className="batch-month-picker">
          <span className="field-label">対象月</span>
          <div className="period-navigation">
            <button aria-label="前月" onClick={() => shiftMonth(-1)}>
              <ChevronLeft />
            </button>
            <label>
              <CalendarDays size={18} />
              <input
                aria-label="対象月"
                type="month"
                value={month}
                onChange={(event) =>
                  event.target.value && changeContext(event.target.value)
                }
              />
            </label>
            <button aria-label="翌月" onClick={() => shiftMonth(1)}>
              <ChevronRight />
            </button>
          </div>
        </div>
        <fieldset className="batch-type-switch">
          <legend>種別</legend>
          <button
            className={type === "expense" ? "active" : ""}
            type="button"
            aria-pressed={type === "expense"}
            onClick={() => changeType("expense")}
          >
            支出
          </button>
          <button
            className={type === "income" ? "active" : ""}
            type="button"
            aria-pressed={type === "income"}
            onClick={() => changeType("income")}
          >
            収入
          </button>
        </fieldset>
        <label className="batch-date-field">
          <span className="field-label">計上日</span>
          <input
            type="date"
            min={`${month}-01`}
            max={monthEnd(month)}
            value={date}
            onChange={(event) => setDate(event.target.value)}
          />
        </label>
        <label className="batch-memo-field">
          <span className="field-label">
            共通メモ <small>任意</small>
          </span>
          <input
            maxLength={200}
            value={memo}
            onChange={(event) => setMemo(event.target.value)}
            placeholder="例：月次まとめ"
          />
        </label>
      </section>

      <div className="batch-guidance" role="note">
        <Info size={19} />
        <div>
          <strong>登録済みデータは上書きしません</strong>
          <span>
            「今回入力」の金額を、選択した計上日で新しい収支として追加します。
          </span>
        </div>
      </div>

      {error && <div className="error">{error}</div>}
      {success && (
        <div className="batch-success" role="status">
          <CheckCircle2 size={19} />
          {success}
        </div>
      )}

      <div className="batch-workspace">
        <section className="batch-entry-card">
          <div className="batch-card-title">
            <div>
              <span>
                {formatMonth(month)}の{typeLabel(type)}
              </span>
              <h2>カテゴリ別入力</h2>
            </div>
            <button
              type="button"
              className="batch-clear-button"
              disabled={!isDirty}
              onClick={clearAmounts}
            >
              <Eraser size={17} />
              すべてクリア
            </button>
          </div>

          <div className="batch-entry-head" aria-hidden="true">
            <span>カテゴリ</span>
            <span>登録済み</span>
            <span>今回入力</span>
          </div>

          {loading ? (
            <div className="batch-empty">カテゴリを読み込んでいます...</div>
          ) : visibleCategories.length === 0 ? (
            <div className="batch-empty">
              {typeLabel(type)}
              カテゴリがありません。先にカテゴリを登録してください。
            </div>
          ) : (
            <div className="batch-category-list">
              {visibleCategories.map((category, index) => {
                const existing = existingByCategory.get(category.uuid);
                const amount = amounts[category.uuid] ?? "";
                return (
                  <div className="batch-category-row" key={category.uuid}>
                    <div className="batch-category-name">
                      <span
                        className="batch-category-marker"
                        aria-hidden="true"
                        data-color={index % 5}
                      >
                        {category.name.slice(0, 1)}
                      </span>
                      <div>
                        <strong>{category.name}</strong>
                        <small>{existing?.count ?? 0}件登録済み</small>
                      </div>
                    </div>
                    <span className="batch-existing-amount">
                      {money.format(existing?.amount ?? 0)}
                    </span>
                    <label className="batch-amount-field">
                      <span className="sr-only">
                        {category.name}の今回入力額
                      </span>
                      <span aria-hidden="true">¥</span>
                      <input
                        aria-label={`${category.name}の今回入力額`}
                        inputMode="numeric"
                        pattern="[0-9]*"
                        value={amount}
                        onChange={(event) =>
                          updateAmount(category.uuid, event.target.value)
                        }
                        onBlur={() =>
                          amount === "0" &&
                          setAmounts((current) => ({
                            ...current,
                            [category.uuid]: "",
                          }))
                        }
                        placeholder="0"
                      />
                    </label>
                  </div>
                );
              })}
            </div>
          )}
        </section>

        <aside className="batch-summary-card" aria-live="polite">
          <div className="batch-summary-icon">
            <ReceiptText size={22} />
          </div>
          <span>今回の入力</span>
          <strong
            className={type === "income" ? "income-amount" : "expense-amount"}
          >
            {type === "income" ? "+" : "-"}
            {money.format(inputTotal)}
          </strong>
          <dl>
            <div>
              <dt>入力カテゴリ</dt>
              <dd>{entries.length}件</dd>
            </div>
            <div>
              <dt>登録済み月額</dt>
              <dd>{money.format(existingTotal)}</dd>
            </div>
            <div>
              <dt>登録後の月額</dt>
              <dd>{money.format(existingTotal + inputTotal)}</dd>
            </div>
          </dl>
          <button
            className="primary batch-review-button"
            disabled={entries.length === 0}
            onClick={openReview}
          >
            <CheckCircle2 size={19} />
            入力内容を確認
          </button>
          <small>
            次の画面で対象カテゴリと金額を確認して、一括登録できます。
          </small>
        </aside>
      </div>

      {reviewOpen && (
        <BatchReviewDialog
          date={date}
          entries={entries}
          memo={memo}
          error={saveError}
          saving={saving}
          type={type}
          close={() => !saving && setReviewOpen(false)}
          save={() => void saveEntries()}
        />
      )}
    </main>
  );
}

function BatchReviewDialog({
  date,
  entries,
  memo,
  error,
  saving,
  type,
  close,
  save,
}: {
  date: string;
  entries: { category: Category; amount: number }[];
  memo: string;
  error: string;
  saving: boolean;
  type: TransactionType;
  close: () => void;
  save: () => void;
}) {
  const total = entries.reduce((sum, entry) => sum + entry.amount, 0);

  useEffect(() => {
    const keyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") close();
    };
    window.addEventListener("keydown", keyDown);
    return () => window.removeEventListener("keydown", keyDown);
  }, [close]);

  return (
    <div
      className="overlay batch-review-overlay"
      onMouseDown={(event) => event.target === event.currentTarget && close()}
    >
      <section
        className="batch-review-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="batch-review-title"
      >
        <div className="dialog-title">
          <div>
            <span>登録前の確認</span>
            <h2 id="batch-review-title">入力内容を確認</h2>
          </div>
          <button
            aria-label="確認画面を閉じる"
            disabled={saving}
            onClick={close}
          >
            <X />
          </button>
        </div>
        <div className="batch-review-meta">
          <div>
            <span>計上日</span>
            <strong>{formatDate(date)}</strong>
          </div>
          <div>
            <span>共通メモ</span>
            <strong>{memo || "なし"}</strong>
          </div>
        </div>
        <div className="batch-review-list">
          {entries.map((entry) => (
            <div key={entry.category.uuid}>
              <span>{entry.category.name}</span>
              <strong
                className={
                  type === "income" ? "income-amount" : "expense-amount"
                }
              >
                {type === "income" ? "+" : "-"}
                {money.format(entry.amount)}
              </strong>
            </div>
          ))}
        </div>
        <div className="batch-review-total">
          <span>合計・{entries.length}件</span>
          <strong>{money.format(total)}</strong>
        </div>
        <div className="batch-review-notice">
          <CircleDollarSign size={19} />
          <span>
            登録すると、{entries.length}件の新しい収支として一括で追加されます。
          </span>
        </div>
        {error && (
          <div className="error" role="alert">
            {error}
          </div>
        )}
        <div className="dialog-actions">
          <button
            type="button"
            className="secondary"
            disabled={saving}
            onClick={close}
          >
            入力に戻る
          </button>
          <button
            type="button"
            className="primary"
            disabled={saving}
            onClick={save}
          >
            {saving ? "登録中..." : "この内容で登録"}
          </button>
        </div>
      </section>
    </div>
  );
}

function monthEnd(month: string) {
  const [year, monthNumber] = month.split("-").map(Number);
  const day = new Date(year, monthNumber, 0).getDate();
  return `${month}-${String(day).padStart(2, "0")}`;
}

function formatMonth(month: string) {
  const [year, monthNumber] = month.split("-");
  return `${year}年${Number(monthNumber)}月`;
}

function formatDate(date: string) {
  return new Date(`${date}T00:00:00`).toLocaleDateString("ja-JP", {
    year: "numeric",
    month: "long",
    day: "numeric",
    weekday: "short",
  });
}

function typeLabel(type: TransactionType) {
  return type === "income" ? "収入" : "支出";
}
