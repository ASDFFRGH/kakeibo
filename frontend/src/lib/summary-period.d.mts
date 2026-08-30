export type SummaryPeriod = "day" | "week" | "month" | "year";

export function resolveMonth(
  value: string | null,
  fallbackDate: string,
): string;

export function shiftAnchor(
  anchor: string,
  period: SummaryPeriod,
  amount: number,
): string;
