import assert from "node:assert/strict";
import test from "node:test";
import {
  gamblingMonthRange,
  gamblingTotals,
  shiftGamblingMonth,
} from "./gambling-records.mjs";

test("calculates stake, payout, and derived balance", () => {
  assert.deepEqual(
    gamblingTotals([
      { stake_amount: 5000, payout_amount: 8000 },
      { stake_amount: 3000, payout_amount: 0 },
      { stake_amount: 0, payout_amount: 1200 },
    ]),
    { stake: 8000, payout: 9200, balance: 1200, count: 3 },
  );
});

test("returns zero totals for an empty month", () => {
  assert.deepEqual(gamblingTotals([]), {
    stake: 0,
    payout: 0,
    balance: 0,
    count: 0,
  });
});

test("creates inclusive month ranges including leap years", () => {
  assert.deepEqual(gamblingMonthRange("2024-02"), {
    from: "2024-02-01",
    to: "2024-02-29",
  });
});

test("shifts months across year boundaries", () => {
  assert.equal(shiftGamblingMonth("2026-01", -1), "2025-12");
  assert.equal(shiftGamblingMonth("2026-12", 1), "2027-01");
});
