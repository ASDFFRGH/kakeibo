import assert from "node:assert/strict";
import test from "node:test";
import {
  gamblingMonthRange,
  gamblingRecordsForMonth,
  gamblingTotals,
  gamblingTotalsByGameType,
  gamblingTotalsByMonth,
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

test("groups records by game type with a deterministic order", () => {
  assert.deepEqual(
    gamblingTotalsByGameType([
      {
        date: "2026-03-02",
        game_type: "競馬",
        stake_amount: 1000,
        payout_amount: 0,
      },
      {
        date: "2026-03-03",
        game_type: "FX",
        stake_amount: 3000,
        payout_amount: 4000,
      },
      {
        date: "2026-03-04",
        game_type: "競馬",
        stake_amount: 500,
        payout_amount: 2000,
      },
    ]),
    [
      { game_type: "FX", stake: 3000, payout: 4000, balance: 1000, count: 1 },
      { game_type: "競馬", stake: 1500, payout: 2000, balance: 500, count: 2 },
    ],
  );
});

test("filters a selected month and groups lifetime totals by newest month", () => {
  const records = [
    {
      date: "2026-02-28",
      game_type: "株",
      stake_amount: 100,
      payout_amount: 120,
    },
    {
      date: "2026-03-01",
      game_type: "FX",
      stake_amount: 400,
      payout_amount: 0,
    },
    {
      date: "2026-03-18",
      game_type: "競艇",
      stake_amount: 300,
      payout_amount: 900,
    },
  ];
  assert.deepEqual(
    gamblingRecordsForMonth(records, "2026-03"),
    records.slice(1),
  );
  assert.deepEqual(gamblingTotalsByMonth(records), [
    { month: "2026-03", stake: 700, payout: 900, balance: 200, count: 2 },
    { month: "2026-02", stake: 100, payout: 120, balance: 20, count: 1 },
  ]);
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
