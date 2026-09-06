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

export function gamblingRecordsForMonth(records, month) {
  return records.filter((record) => record.date.startsWith(`${month}-`));
}

export function gamblingTotalsByGameType(records) {
  const groups = new Map();
  for (const record of records) {
    const gameType = record.game_type;
    const group = groups.get(gameType) ?? {
      game_type: gameType,
      stake: 0,
      payout: 0,
      balance: 0,
      count: 0,
    };
    group.stake += record.stake_amount;
    group.payout += record.payout_amount;
    group.balance += record.payout_amount - record.stake_amount;
    group.count += 1;
    groups.set(gameType, group);
  }
  return [...groups.values()].sort(
    (left, right) =>
      right.stake - left.stake ||
      left.game_type.localeCompare(right.game_type, "ja"),
  );
}

export function gamblingTotalsByMonth(records) {
  const groups = new Map();
  for (const record of records) {
    const month = record.date.slice(0, 7);
    const group = groups.get(month) ?? {
      month,
      stake: 0,
      payout: 0,
      balance: 0,
      count: 0,
    };
    group.stake += record.stake_amount;
    group.payout += record.payout_amount;
    group.balance += record.payout_amount - record.stake_amount;
    group.count += 1;
    groups.set(month, group);
  }
  return [...groups.values()].sort((left, right) =>
    right.month.localeCompare(left.month),
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
