import type { Category, Expense } from "./api";

export function orderCategoriesByExpenseFrequency(
  categories: Category[],
  expenses: Expense[],
): Category[];

export function orderCategoriesByAmount(
  categories: Category[],
  expenses: Expense[],
): Category[];
