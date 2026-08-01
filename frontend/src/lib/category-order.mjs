export function orderCategoriesByExpenseFrequency(categories, expenses) {
  const counts = new Map();
  for (const expense of expenses) {
    if (expense.deleted_at !== null) continue;
    counts.set(
      expense.category_uuid,
      (counts.get(expense.category_uuid) ?? 0) + 1,
    );
  }

  return categories
    .map((category, originalPosition) => ({ category, originalPosition }))
    .sort(
      (left, right) =>
        (counts.get(right.category.uuid) ?? 0) -
          (counts.get(left.category.uuid) ?? 0) ||
        left.originalPosition - right.originalPosition,
    )
    .map(({ category }) => category);
}

export function orderCategoriesByAmount(categories, expenses) {
  const amounts = new Map();
  for (const expense of expenses) {
    if (expense.deleted_at !== null) continue;
    amounts.set(
      expense.category_uuid,
      (amounts.get(expense.category_uuid) ?? 0) + expense.amount,
    );
  }

  return categories
    .map((category, originalPosition) => ({ category, originalPosition }))
    .sort(
      (left, right) =>
        (amounts.get(right.category.uuid) ?? 0) -
          (amounts.get(left.category.uuid) ?? 0) ||
        left.originalPosition - right.originalPosition,
    )
    .map(({ category }) => category);
}
