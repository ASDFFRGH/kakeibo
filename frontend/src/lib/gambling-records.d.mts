export type GamblingAmounts = {
  stake_amount: number;
  payout_amount: number;
};

export type GamblingTotals = {
  stake: number;
  payout: number;
  balance: number;
  count: number;
};

export function gamblingTotals(records: GamblingAmounts[]): GamblingTotals;
export function gamblingMonthRange(month: string): {
  from: string;
  to: string;
};
export function shiftGamblingMonth(month: string, amount: number): string;
