import assert from "node:assert/strict";
import test from "node:test";
import { localToday, monthEnd } from "./calendar-date.mjs";

test("returns the last calendar day without a UTC timezone shift", () => {
  assert.equal(monthEnd("2026-06"), "2026-06-30");
  assert.equal(monthEnd("2026-08"), "2026-08-31");
});

test("handles leap years", () => {
  assert.equal(monthEnd("2024-02"), "2024-02-29");
  assert.equal(monthEnd("2026-02"), "2026-02-28");
});

test("formats a local calendar date without converting it to UTC", () => {
  assert.equal(localToday(new Date(2026, 5, 30, 0, 30)), "2026-06-30");
});
