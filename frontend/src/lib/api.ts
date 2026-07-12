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
  category_uuid: string;
  memo: string;
  created_at: string;
  updated_at: string;
  deleted_at: string | null;
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
    x: Pick<Expense, "uuid" | "date" | "amount" | "category_uuid" | "memo">,
    editing: boolean,
  ) =>
    request<Expense>(editing ? `/expenses/${x.uuid}` : "/expenses", {
      method: editing ? "PUT" : "POST",
      body: JSON.stringify(
        editing
          ? {
              date: x.date,
              amount: x.amount,
              category_uuid: x.category_uuid,
              memo: x.memo,
            }
          : x,
      ),
    }),
  deleteExpense: (id: string) =>
    request<void>(`/expenses/${id}`, { method: "DELETE" }),
};
