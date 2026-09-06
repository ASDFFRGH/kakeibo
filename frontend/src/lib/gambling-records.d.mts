export type GamblingAmounts = {
  stake_amount: number;
  payout_amount: number;
};

export type GamblingRecordForSummary = GamblingAmounts & {
  date: string;
  game_type: string;
};

export type GamblingTotals = {
  stake: number;
  payout: number;
  balance: number;
  count: number;
};

export function gamblingTotals(records: GamblingAmounts[]): GamblingTotals;
export function gamblingRecordsForMonth(
  records: GamblingRecordForSummary[],
  month: string,
): GamblingRecordForSummary[];
export function gamblingTotalsByGameType(
  records: GamblingRecordForSummary[],
): Array<GamblingTotals & { game_type: string }>;
export function gamblingTotalsByMonth(
  records: GamblingRecordForSummary[],
): Array<GamblingTotals & { month: string }>;
export function gamblingMonthRange(month: string): {
  from: string;
  to: string;
};
export function shiftGamblingMonth(month: string, amount: number): string;
