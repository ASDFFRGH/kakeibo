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
  BarChart3,
  CalendarDays,
  ChevronLeft,
  ChevronRight,
  ClipboardList,
  LineChart,
  Menu,
  Pencil,
  Plus,
  RefreshCw,
  RotateCcw,
  Settings,
  Trash2,
  WalletCards,
  X,
} from "lucide-react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { api, Category, Expense } from "../lib/api";
import { localToday, monthEnd } from "../lib/calendar-date.mjs";
import { orderCategoriesByExpenseFrequency } from "../lib/category-order.mjs";
import { resolveMonth } from "../lib/summary-period.mjs";
import { LedgerNavigation } from "./components/LedgerNavigation";

const money = new Intl.NumberFormat("ja-JP", {
  style: "currency",
  currency: "JPY",
  maximumFractionDigits: 0,
});
export default function HomePage() {
  return (
    <Suspense fallback={<main className="calendar-page" />}>
      <Home />
    </Suspense>
  );
}

function Home() {
  const searchParams = useSearchParams();
  const [categories, setCategories] = useState<Category[]>([]),
    [expenses, setExpenses] = useState<Expense[]>([]),
    [loading, setLoading] = useState(true),
    [error, setError] = useState("");
  const [month, setMonth] = useState(() =>
      resolveMonth(searchParams.get("month"), localToday()),
    ),
    [selectedDate, setSelectedDate] = useState(() =>
      searchParams.has("month") ? `${month}-01` : localToday(),
    ),
    [navOpen, setNavOpen] = useState(false),
    [dialog, setDialog] = useState<"expense" | "category" | "trash" | null>(
      null,
    ),
    [editing, setEditing] = useState<Expense | null>(null);
  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const from = `${month}-01`,
        to = monthEnd(month),
        query = `?from=${from}&to=${to}`;
      const [c, e, allExpenses] = await Promise.all([
        api.categories(),
        api.expenses(query),
        api.expenses(),
      ]);
      setCategories(orderCategoriesByExpenseFrequency(c, allExpenses));
      setExpenses(e);
    } catch (e) {
      setError(e instanceof Error ? e.message : "読込に失敗しました");
    } finally {
      setLoading(false);
    }
  }, [month]);
  useEffect(() => {
    void load();
  }, [load]);
  const totals = useMemo(() => {
    const income = expenses
      .filter((x) => x.type === "income")
      .reduce((sum, x) => sum + x.amount, 0);
    const expense = expenses
      .filter((x) => x.type !== "income")
      .reduce((sum, x) => sum + x.amount, 0);
    return { income, expense, balance: income - expense };
  }, [expenses]);
  const selectedExpenses = useMemo(
    () => expenses.filter((expense) => expense.date === selectedDate),
    [expenses, selectedDate],
  );
  const dailyBalances = useMemo(() => {
    const balances = new Map<string, number>();
    expenses.forEach((expense) => {
      const signed =
        expense.type === "income" ? expense.amount : -expense.amount;
      balances.set(expense.date, (balances.get(expense.date) ?? 0) + signed);
    });
    return balances;
  }, [expenses]);
  const calendarDays = useMemo(() => buildCalendarDays(month), [month]);
  const shift = (delta: number) => {
    const d = new Date(
      Number(month.slice(0, 4)),
      Number(month.slice(5, 7)) - 1 + delta,
      1,
    );
    const nextMonth = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`;
    const currentDay = Number(selectedDate.slice(8, 10));
    const nextDay = Math.min(
      currentDay,
      Number(monthEnd(nextMonth).slice(8, 10)),
    );
    setMonth(nextMonth);
    setSelectedDate(`${nextMonth}-${String(nextDay).padStart(2, "0")}`);
  };
  const remove = async (x: Expense) => {
    if (!confirm(`${x.memo || "この収支"}を削除しますか？`)) return;
    await api.deleteExpense(x.uuid);
    await load();
  };
  const closeDialog = () => {
    setDialog(null);
    setEditing(null);
  };
  return (
    <main className="calendar-page">
      <header className="app-topbar">
        <button
          className="menu-button"
          aria-label="メニューを開く"
          onClick={() => setNavOpen(true)}
        >
          <Menu />
        </button>
        <div className="brand">
          <WalletCards />
          <span>My 家計簿</span>
        </div>
        <nav className="desktop-nav" aria-label="メインナビゲーション">
          <Link className="active" href="/">
            <CalendarDays size={17} />
            カレンダー
          </Link>
          <Link href={`/summary?month=${month}`}>
            <BarChart3 size={17} />
            サマリー
          </Link>
          <Link href="/graph">
            <LineChart size={17} />
            グラフ
          </Link>
          <Link href="/monthly-entry">
            <ClipboardList size={17} />
            まとめて入力
          </Link>
        </nav>
        <div className="desktop-actions">
          <button title="カテゴリ" onClick={() => setDialog("category")}>
            <Settings size={18} />
          </button>
          <button title="ゴミ箱" onClick={() => setDialog("trash")}>
            <Trash2 size={18} />
          </button>
          <button title="再読み込み" onClick={() => void load()}>
            <RefreshCw size={18} />
          </button>
          <button
            className="primary"
            onClick={() => {
              setEditing(null);
              setDialog("expense");
            }}
          >
            <Plus size={18} />
            収支を追加
          </button>
        </div>
      </header>

      <LedgerNavigation active="household" month={month} />

      {navOpen && (
        <div
          className="nav-scrim"
          onMouseDown={(event) =>
            event.target === event.currentTarget && setNavOpen(false)
          }
        >
          <aside className="app-drawer" aria-label="メインメニュー">
            <h1>My 家計簿</h1>
            <button className="active" onClick={() => setNavOpen(false)}>
              <CalendarDays />
              カレンダー
            </button>
            <Link href={`/summary?month=${month}`}>
              <BarChart3 />
              サマリー
            </Link>
            <Link href="/graph">
              <LineChart />
              グラフ
            </Link>
            <button
              onClick={() => {
                setNavOpen(false);
                setDialog("category");
              }}
            >
              <Settings />
              カテゴリ
            </button>
            <button
              onClick={() => {
                setNavOpen(false);
                setDialog("trash");
              }}
            >
              <Trash2 />
              ゴミ箱
            </button>
            <Link href="/monthly-entry">
              <ClipboardList />
              まとめて入力
            </Link>
          </aside>
        </div>
      )}

      {error && <div className="error">{error}</div>}
      <div className="calendar-dashboard">
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
                    {dailyBalances.has(date) && (
                      <small
                        className={
                          (dailyBalances.get(date) ?? 0) >= 0
                            ? "income-amount"
                            : "expense-amount"
                        }
                      >
                        {signedMoney(dailyBalances.get(date) ?? 0)}
                      </small>
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
              <small>収入</small>
              <strong className="income-amount">
                {money.format(totals.income)}
              </strong>
            </span>
            <span>
              <small>支出</small>
              <strong className="expense-amount">
                {money.format(totals.expense)}
              </strong>
            </span>
            <span>
              <small>収支</small>
              <strong
                className={
                  totals.balance >= 0 ? "income-amount" : "expense-amount"
                }
              >
                {signedMoney(totals.balance)}
              </strong>
            </span>
          </div>
        </div>

        <section className="selected-day">
          <div className="selected-day-heading">
            <span>選択日の明細</span>
            <h2>{formatSelectedDate(selectedDate)}</h2>
            <Link href="/monthly-entry">
              <ClipboardList size={17} />
              まとめて入力
            </Link>
          </div>
          {loading ? (
            <div className="day-empty">読み込み中...</div>
          ) : selectedExpenses.length === 0 ? (
            <div className="day-empty">この日の収支はありません</div>
          ) : (
            <div className="transaction-cards">
              {selectedExpenses.map((expense) => {
                const category =
                  categories.find((item) => item.uuid === expense.category_uuid)
                    ?.name ?? "未分類";
                return (
                  <article className="transaction-card" key={expense.uuid}>
                    <button
                      className="transaction-card-main"
                      onClick={() => {
                        setEditing(expense);
                        setDialog("expense");
                      }}
                    >
                      <span>
                        <strong>{category}</strong>
                        {expense.memo && <small>{expense.memo}</small>}
                      </span>
                      <b
                        className={
                          expense.type === "income"
                            ? "income-amount"
                            : "expense-amount"
                        }
                      >
                        {expense.type === "income" ? "+" : "-"}
                        {money.format(expense.amount)}
                      </b>
                      <ChevronRight />
                    </button>
                    <button
                      className="transaction-delete"
                      title="削除"
                      onClick={() => void remove(expense)}
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

      <button
        className="calendar-fab"
        onClick={() => {
          setEditing(null);
          setDialog("expense");
        }}
      >
        <Plus />
        収支を追加
      </button>
      {dialog === "expense" && (
        <ExpenseDialog
          categories={categories}
          expense={editing}
          close={closeDialog}
          saved={async () => {
            closeDialog();
            await load();
          }}
        />
      )}
      {dialog === "category" && (
        <CategoryDialog
          categories={categories}
          close={() => setDialog(null)}
          changed={load}
        />
      )}
      {dialog === "trash" && (
        <TrashDialog
          categories={categories}
          close={() => setDialog(null)}
          changed={load}
        />
      )}
    </main>
  );
}

function buildCalendarDays(month: string): (string | null)[] {
  const [year, monthNumber] = month.split("-").map(Number);
  const firstWeekday = new Date(year, monthNumber - 1, 1).getDay();
  const lastDay = Number(monthEnd(month).slice(8, 10));
  return [
    ...Array<null>(firstWeekday).fill(null),
    ...Array.from(
      { length: lastDay },
      (_, index) => `${month}-${String(index + 1).padStart(2, "0")}`,
    ),
  ];
}

function formatMonth(month: string) {
  const [year, monthNumber] = month.split("-");
  return `${year}年${Number(monthNumber)}月`;
}

function formatSelectedDate(date: string) {
  return new Date(`${date}T00:00:00`).toLocaleDateString("ja-JP", {
    month: "long",
    day: "numeric",
    weekday: "short",
  });
}

function signedMoney(value: number) {
  if (value === 0) return money.format(0);
  return `${value > 0 ? "+" : "-"}${money.format(Math.abs(value))}`;
}
function ExpenseDialog({
  categories,
  expense,
  close,
  saved,
}: {
  categories: Category[];
  expense: Expense | null;
  close: () => void;
  saved: () => Promise<void>;
}) {
  const initialType = expense?.type ?? "expense";
  const [date, setDate] = useState(expense?.date ?? localToday()),
    [amount, setAmount] = useState(expense?.amount.toString() ?? ""),
    [type, setType] = useState<Expense["type"]>(initialType),
    [category, setCategory] = useState(
      expense?.category_uuid ??
        categories.find((c) => c.type === initialType)?.uuid ??
        "",
    ),
    [memo, setMemo] = useState(expense?.memo ?? ""),
    [busy, setBusy] = useState(false);
  const availableCategories = categories.filter((c) => c.type === type);
  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    try {
      await api.saveExpense(
        {
          uuid: expense?.uuid ?? crypto.randomUUID(),
          date,
          amount: Number(amount),
          type,
          category_uuid: category,
          memo,
        },
        !!expense,
      );
      await saved();
    } finally {
      setBusy(false);
    }
  };
  return (
    <div
      className="overlay"
      onMouseDown={(e) => e.target === e.currentTarget && close()}
    >
      <form className="dialog" onSubmit={submit}>
        <div className="dialog-title">
          <h2>{expense ? "収支を編集" : "収支を追加"}</h2>
          <button type="button" onClick={close}>
            <X />
          </button>
        </div>
        <fieldset className="transaction-type">
          <legend>種別</legend>
          <label>
            <input
              type="radio"
              name="type"
              value="expense"
              checked={type === "expense"}
              onChange={() => {
                setType("expense");
                setCategory(
                  categories.find((c) => c.type === "expense")?.uuid ?? "",
                );
              }}
            />
            支出
          </label>
          <label>
            <input
              type="radio"
              name="type"
              value="income"
              checked={type === "income"}
              onChange={() => {
                setType("income");
                setCategory(
                  categories.find((c) => c.type === "income")?.uuid ?? "",
                );
              }}
            />
            収入
          </label>
        </fieldset>
        <label>
          日付
          <input
            required
            type="date"
            value={date}
            onChange={(e) => setDate(e.target.value)}
          />
        </label>
        <label>
          金額
          <input
            required
            min="1"
            inputMode="numeric"
            type="number"
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            placeholder="0"
          />
        </label>
        <label>
          {type === "income" ? "収入" : "支出"}カテゴリ
          <select
            required
            value={category}
            onChange={(e) => setCategory(e.target.value)}
          >
            {availableCategories.map((c) => (
              <option key={c.uuid} value={c.uuid}>
                {c.name}
              </option>
            ))}
          </select>
          {availableCategories.length === 0 && (
            <small className="field-error">
              {type === "income" ? "収入" : "支出"}
              カテゴリを先に作成してください
            </small>
          )}
        </label>
        <label>
          メモ
          <input
            maxLength={200}
            value={memo}
            onChange={(e) => setMemo(e.target.value)}
            placeholder="任意"
          />
        </label>
        <div className="dialog-actions">
          <button type="button" className="secondary" onClick={close}>
            キャンセル
          </button>
          <button className="primary" disabled={busy || !category}>
            {busy ? "保存中..." : "保存"}
          </button>
        </div>
      </form>
    </div>
  );
}
function CategoryDialog({
  categories,
  close,
  changed,
}: {
  categories: Category[];
  close: () => void;
  changed: () => Promise<void>;
}) {
  const [name, setName] = useState(""),
    [type, setType] = useState<Category["type"]>("expense"),
    [edit, setEdit] = useState<Category | null>(null);
  const visibleCategories = categories.filter(
    (category) => category.type === type,
  );
  const clearEditing = () => {
    setName("");
    setEdit(null);
  };
  const submit = async (e: FormEvent) => {
    e.preventDefault();
    await api.saveCategory(
      { uuid: edit?.uuid ?? crypto.randomUUID(), name, type },
      !!edit,
    );
    clearEditing();
    await changed();
  };
  const remove = async (c: Category) => {
    if (!confirm(`${c.name}を削除しますか？`)) return;
    await api.deleteCategory(c.uuid);
    await changed();
  };
  return (
    <div className="overlay">
      <section className="dialog categories-dialog">
        <div className="dialog-title">
          <h2>カテゴリ管理</h2>
          <button onClick={close}>
            <X />
          </button>
        </div>
        <fieldset className="transaction-type category-type">
          <legend>カテゴリの種別</legend>
          <label>
            <input
              type="radio"
              name="category-type"
              value="expense"
              checked={type === "expense"}
              disabled={edit !== null}
              onChange={() => {
                setType("expense");
                clearEditing();
              }}
            />
            支出
          </label>
          <label>
            <input
              type="radio"
              name="category-type"
              value="income"
              checked={type === "income"}
              disabled={edit !== null}
              onChange={() => {
                setType("income");
                clearEditing();
              }}
            />
            収入
          </label>
        </fieldset>
        <form className="inline" onSubmit={submit}>
          <input
            required
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="カテゴリ名"
          />
          <button className="primary">{edit ? "更新" : "追加"}</button>
        </form>
        <div className="category-list">
          {visibleCategories.length === 0 && (
            <div className="empty-category">
              {type === "income" ? "収入" : "支出"}カテゴリはまだありません
            </div>
          )}
          {visibleCategories.map((c) => (
            <div key={c.uuid}>
              <span>
                {c.name}
                <small className={`type-badge ${c.type}`}>
                  {c.type === "income" ? "収入" : "支出"}
                </small>
              </span>
              <span>
                <button
                  title="編集"
                  onClick={() => {
                    setEdit(c);
                    setName(c.name);
                    setType(c.type);
                  }}
                >
                  <Pencil size={16} />
                </button>
                <button title="削除" onClick={() => void remove(c)}>
                  <Trash2 size={16} />
                </button>
              </span>
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}

function TrashDialog({
  categories,
  close,
  changed,
}: {
  categories: Category[];
  close: () => void;
  changed: () => Promise<void>;
}) {
  const [deletedCategories, setDeletedCategories] = useState<Category[]>([]),
    [deletedExpenses, setDeletedExpenses] = useState<Expense[]>([]),
    [loading, setLoading] = useState(true),
    [error, setError] = useState(""),
    [restoringId, setRestoringId] = useState("");

  const loadTrash = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const trash = await api.trash();
      setDeletedCategories(trash.categories);
      setDeletedExpenses(trash.expenses);
    } catch (e) {
      setError(e instanceof Error ? e.message : "ゴミ箱の読込に失敗しました");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadTrash();
  }, [loadTrash]);

  const restoreCategory = async (category: Category) => {
    setRestoringId(category.uuid);
    setError("");
    try {
      await api.restoreCategory(category.uuid);
      await Promise.all([loadTrash(), changed()]);
    } catch (e) {
      setError(e instanceof Error ? e.message : "復元に失敗しました");
    } finally {
      setRestoringId("");
    }
  };
  const restoreExpense = async (expense: Expense) => {
    setRestoringId(expense.uuid);
    setError("");
    try {
      await api.restoreExpense(expense.uuid);
      await Promise.all([loadTrash(), changed()]);
    } catch (e) {
      setError(e instanceof Error ? e.message : "復元に失敗しました");
    } finally {
      setRestoringId("");
    }
  };
  const allCategories = [...categories, ...deletedCategories];

  return (
    <div className="overlay">
      <section className="dialog trash-dialog">
        <div className="dialog-title">
          <h2>ゴミ箱</h2>
          <button onClick={close}>
            <X />
          </button>
        </div>
        <p>復元したデータは通常の一覧へ戻ります。</p>
        {error && <div className="error">{error}</div>}
        {loading ? (
          <div className="empty">読み込み中...</div>
        ) : deletedExpenses.length === 0 && deletedCategories.length === 0 ? (
          <div className="empty">削除済みデータはありません</div>
        ) : (
          <div className="trash-list">
            {deletedExpenses.length > 0 && <h3>収支</h3>}
            {deletedExpenses.map((expense) => (
              <div key={expense.uuid}>
                <span>
                  <strong
                    className={
                      expense.type === "income"
                        ? "income-amount"
                        : "expense-amount"
                    }
                  >
                    {expense.memo ||
                      (expense.type === "income" ? "収入" : "支出")}
                  </strong>
                  <small>
                    {expense.date}・
                    {allCategories.find(
                      (category) => category.uuid === expense.category_uuid,
                    )?.name ?? "未分類"}
                  </small>
                </span>
                <button
                  className="secondary"
                  disabled={restoringId !== ""}
                  onClick={() => void restoreExpense(expense)}
                >
                  <RotateCcw size={16} />
                  {restoringId === expense.uuid ? "復元中..." : "復元"}
                </button>
              </div>
            ))}
            {deletedCategories.length > 0 && <h3>カテゴリ</h3>}
            {deletedCategories.map((category) => (
              <div key={category.uuid}>
                <span>
                  {category.name}
                  <small>
                    {category.type === "income"
                      ? "収入カテゴリ"
                      : "支出カテゴリ"}
                  </small>
                </span>
                <button
                  className="secondary"
                  disabled={restoringId !== ""}
                  onClick={() => void restoreCategory(category)}
                >
                  <RotateCcw size={16} />
                  {restoringId === category.uuid ? "復元中..." : "復元"}
                </button>
              </div>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}
