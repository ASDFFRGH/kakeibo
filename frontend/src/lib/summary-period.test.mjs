import assert from "node:assert/strict";
import test from "node:test";
import { shiftAnchor } from "./summary-period.mjs";

test("day navigation crosses month boundaries", () => {
  assert.equal(shiftAnchor("2026-07-31", "day", 1), "2026-08-01");
});

test("week navigation moves by seven days", () => {
  assert.equal(shiftAnchor("2026-07-14", "week", -1), "2026-07-07");
});

test("month navigation uses the first day of the destination month", () => {
  assert.equal(shiftAnchor("2026-03-31", "month", -1), "2026-02-01");
});

test("year navigation uses the first day of the destination year", () => {
  assert.equal(shiftAnchor("2026-07-14", "year", 1), "2027-01-01");
});
