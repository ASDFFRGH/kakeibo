import type { Category, Expense } from "./api";

export function orderCategoriesByExpenseFrequency(
  categories: Category[],
  expenses: Expense[],
): Category[];
