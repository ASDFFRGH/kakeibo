import { monthEnd } from "./calendar-date.mjs";

export function gamblingTotals(records) {
  return records.reduce(
    (totals, record) => {
      totals.stake += record.stake_amount;
      totals.payout += record.payout_amount;
      totals.balance += record.payout_amount - record.stake_amount;
      totals.count += 1;
      return totals;
    },
    { stake: 0, payout: 0, balance: 0, count: 0 },
  );
}

export function gamblingMonthRange(month) {
  return { from: `${month}-01`, to: monthEnd(month) };
}

export function shiftGamblingMonth(month, amount) {
  const [year, monthNumber] = month.split("-").map(Number);
  const shifted = new Date(Date.UTC(year, monthNumber - 1 + amount, 1));
  return `${shifted.getUTCFullYear()}-${String(shifted.getUTCMonth() + 1).padStart(2, "0")}`;
}
