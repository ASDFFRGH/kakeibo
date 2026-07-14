"use client";
import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import {
  BarChart3,
  CalendarDays,
  ChevronLeft,
  ChevronRight,
  Pencil,
  Plus,
  RefreshCw,
  Search,
  Settings,
  Trash2,
  WalletCards,
  X,
} from "lucide-react";
import Link from "next/link";
import { api, Category, Expense } from "../lib/api";

const today = () => new Date().toISOString().slice(0, 10);
const money = new Intl.NumberFormat("ja-JP", {
  style: "currency",
  currency: "JPY",
});
export default function Home() {
  const [categories, setCategories] = useState<Category[]>([]),
    [expenses, setExpenses] = useState<Expense[]>([]),
    [loading, setLoading] = useState(true),
    [error, setError] = useState("");
  const [month, setMonth] = useState(today().slice(0, 7)),
    [categoryFilter, setCategoryFilter] = useState(""),
    [dialog, setDialog] = useState<"expense" | "category" | null>(null),
    [editing, setEditing] = useState<Expense | null>(null);
  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const from = `${month}-01`,
        to = new Date(Number(month.slice(0, 4)), Number(month.slice(5, 7)), 0)
          .toISOString()
          .slice(0, 10),
        query = `?from=${from}&to=${to}${categoryFilter ? `&category_uuid=${categoryFilter}` : ""}`;
      const [c, e] = await Promise.all([api.categories(), api.expenses(query)]);
      setCategories(c);
      setExpenses(e);
    } catch (e) {
      setError(e instanceof Error ? e.message : "読込に失敗しました");
    } finally {
      setLoading(false);
    }
  }, [month, categoryFilter]);
  useEffect(() => {
    void load();
  }, [load]);
  const total = useMemo(
    () => expenses.reduce((sum, x) => sum + x.amount, 0),
    [expenses],
  );
  const shift = (delta: number) => {
    const d = new Date(
      Number(month.slice(0, 4)),
      Number(month.slice(5, 7)) - 1 + delta,
      1,
    );
    setMonth(`${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`);
  };
  const remove = async (x: Expense) => {
    if (!confirm(`${x.memo || "この支出"}を削除しますか？`)) return;
    await api.deleteExpense(x.uuid);
    await load();
  };
  return (
    <main>
      <header>
        <div className="brand">
          <WalletCards />
          <span>My 家計簿</span>
        </div>
        <div className="header-actions">
          <Link className="secondary" href="/summary">
            <BarChart3 size={18} />
            サマリー
          </Link>
          <button className="secondary" onClick={() => setDialog("category")}>
            <Settings size={18} />
            カテゴリ
          </button>
        </div>
      </header>
      <section className="summary">
        <div>
          <span>今月の支出</span>
          <strong>{money.format(total)}</strong>
          <small>{expenses.length}件</small>
        </div>
        <button
          className="primary"
          onClick={() => {
            setEditing(null);
            setDialog("expense");
          }}
        >
          <Plus size={19} />
          支出を追加
        </button>
      </section>
      <section className="toolbar">
        <div className="month">
          <button aria-label="前月" onClick={() => shift(-1)}>
            <ChevronLeft />
          </button>
          <span>
            <CalendarDays size={18} />
            {month.replace("-", "年")}月
          </span>
          <button aria-label="翌月" onClick={() => shift(1)}>
            <ChevronRight />
          </button>
        </div>
        <label>
          <Search size={17} />
          <select
            value={categoryFilter}
            onChange={(e) => setCategoryFilter(e.target.value)}
          >
            <option value="">すべてのカテゴリ</option>
            {categories.map((c) => (
              <option key={c.uuid} value={c.uuid}>
                {c.name}
              </option>
            ))}
          </select>
        </label>
        <button className="icon" title="再読み込み" onClick={() => void load()}>
          <RefreshCw size={18} />
        </button>
      </section>
      {error && <div className="error">{error}</div>}
      <section className="table">
        <div className="thead">
          <span>日付</span>
          <span>カテゴリ</span>
          <span>メモ</span>
          <span>金額</span>
          <span />
        </div>
        {loading ? (
          <div className="empty">読み込み中...</div>
        ) : expenses.length === 0 ? (
          <div className="empty">この月の支出はありません</div>
        ) : (
          expenses.map((x) => (
            <div className="row" key={x.uuid}>
              <time>{x.date.slice(5).replace("-", "/")}</time>
              <span className="category">
                {categories.find((c) => c.uuid === x.category_uuid)?.name ??
                  "未分類"}
              </span>
              <span className="memo">{x.memo || "-"}</span>
              <strong>{money.format(x.amount)}</strong>
              <span className="actions">
                <button
                  title="編集"
                  onClick={() => {
                    setEditing(x);
                    setDialog("expense");
                  }}
                >
                  <Pencil size={17} />
                </button>
                <button title="削除" onClick={() => void remove(x)}>
                  <Trash2 size={17} />
                </button>
              </span>
            </div>
          ))
        )}
      </section>
      {dialog === "expense" && (
        <ExpenseDialog
          categories={categories}
          expense={editing}
          close={() => setDialog(null)}
          saved={async () => {
            setDialog(null);
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
    </main>
  );
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
  const [date, setDate] = useState(expense?.date ?? today()),
    [amount, setAmount] = useState(expense?.amount.toString() ?? ""),
    [category, setCategory] = useState(
      expense?.category_uuid ?? categories[0]?.uuid ?? "",
    ),
    [memo, setMemo] = useState(expense?.memo ?? ""),
    [busy, setBusy] = useState(false);
  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    try {
      await api.saveExpense(
        {
          uuid: expense?.uuid ?? crypto.randomUUID(),
          date,
          amount: Number(amount),
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
          <h2>{expense ? "支出を編集" : "支出を追加"}</h2>
          <button type="button" onClick={close}>
            <X />
          </button>
        </div>
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
          カテゴリ
          <select
            required
            value={category}
            onChange={(e) => setCategory(e.target.value)}
          >
            {categories.map((c) => (
              <option key={c.uuid} value={c.uuid}>
                {c.name}
              </option>
            ))}
          </select>
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
    [edit, setEdit] = useState<Category | null>(null);
  const submit = async (e: FormEvent) => {
    e.preventDefault();
    await api.saveCategory(
      { uuid: edit?.uuid ?? crypto.randomUUID(), name },
      !!edit,
    );
    setName("");
    setEdit(null);
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
          {categories.map((c) => (
            <div key={c.uuid}>
              <span>{c.name}</span>
              <span>
                <button
                  title="編集"
                  onClick={() => {
                    setEdit(c);
                    setName(c.name);
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
