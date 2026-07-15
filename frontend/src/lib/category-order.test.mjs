import assert from "node:assert/strict";
import test from "node:test";
import { orderCategoriesByExpenseFrequency } from "./category-order.mjs";

const category = (uuid) => ({ uuid });
const expense = (category_uuid, deleted_at = null) => ({
  category_uuid,
  deleted_at,
});

test("orders categories by active expense count descending", () => {
  const categories = [category("daily"), category("food"), category("travel")];
  const expenses = [
    expense("food"),
    expense("travel"),
    expense("food"),
    expense("food", "2026-07-15T00:00:00Z"),
  ];

  const result = orderCategoriesByExpenseFrequency(categories, expenses);

  assert.deepEqual(
    result.map(({ uuid }) => uuid),
    ["food", "travel", "daily"],
  );
});

test("retains the existing order when frequencies are equal", () => {
  const categories = [category("daily"), category("food"), category("travel")];
  const expenses = [expense("food"), expense("daily")];

  const result = orderCategoriesByExpenseFrequency(categories, expenses);

  assert.deepEqual(
    result.map(({ uuid }) => uuid),
    ["daily", "food", "travel"],
  );
});
