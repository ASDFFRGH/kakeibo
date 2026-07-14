export type SummaryPeriod = "day" | "week" | "month" | "year";

export function shiftAnchor(
  anchor: string,
  period: SummaryPeriod,
  amount: number,
): string;
