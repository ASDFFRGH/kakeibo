export type Category = {
  uuid: string;
  name: string;
  created_at: string;
  updated_at: string;
  deleted_at: string | null;
};
export type Expense = {
  uuid: string;
  date: string;
  amount: number;
  type: "expense" | "income";
  category_uuid: string;
  memo: string;
  created_at: string;
  updated_at: string;
  deleted_at: string | null;
};
export type TrashData = {
  categories: Category[];
  expenses: Expense[];
};
export type SummaryPeriod = "day" | "week" | "month" | "year";
export type SummaryBucket = {
  key: string;
  from: string;
  to: string;
  expense_amount: number;
  income_amount: number;
  balance: number;
  expense_count: number;
  income_count: number;
};
export type Summary = {
  period: SummaryPeriod;
  anchor_date: string;
  from: string;
  to: string;
  total_expense: number;
  total_income: number;
  balance: number;
  expense_count: number;
  income_count: number;
  buckets: SummaryBucket[];
};
type Envelope<T> = { success: boolean; data: T; message?: string };
const base =
  process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080/api/v1";
async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(base + path, {
    ...init,
    headers: { "Content-Type": "application/json", ...init?.headers },
    cache: "no-store",
  });
  if (response.status === 204) return undefined as T;
  const body = (await response.json()) as Envelope<T>;
  if (!response.ok || !body.success)
    throw new Error(body.message ?? "通信に失敗しました");
  return body.data;
}
export const api = {
  categories: () => request<Category[]>("/categories"),
  saveCategory: (x: Pick<Category, "uuid" | "name">, editing: boolean) =>
    request<Category>(editing ? `/categories/${x.uuid}` : "/categories", {
      method: editing ? "PUT" : "POST",
      body: JSON.stringify(editing ? { name: x.name } : x),
    }),
  deleteCategory: (id: string) =>
    request<void>(`/categories/${id}`, { method: "DELETE" }),
  expenses: (query = "") => request<Expense[]>(`/expenses${query}`),
  saveExpense: (
    x: Pick<
      Expense,
      "uuid" | "date" | "amount" | "type" | "category_uuid" | "memo"
    >,
    editing: boolean,
  ) =>
    request<Expense>(editing ? `/expenses/${x.uuid}` : "/expenses", {
      method: editing ? "PUT" : "POST",
      body: JSON.stringify(
        editing
          ? {
              date: x.date,
              amount: x.amount,
              type: x.type,
              category_uuid: x.category_uuid,
              memo: x.memo,
            }
          : x,
      ),
    }),
  deleteExpense: (id: string) =>
    request<void>(`/expenses/${id}`, { method: "DELETE" }),
  trash: () => request<TrashData>("/trash"),
  restoreCategory: (id: string) =>
    request<void>(`/categories/${id}/restore`, { method: "POST" }),
  restoreExpense: (id: string) =>
    request<void>(`/expenses/${id}/restore`, { method: "POST" }),
  summary: (period: SummaryPeriod, date: string) =>
    request<Summary>(
      `/summaries?period=${encodeURIComponent(period)}&date=${encodeURIComponent(date)}`,
    ),
};
