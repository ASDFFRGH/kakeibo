const parseDate = (value) => {
  const [year, month, day] = value.split("-").map(Number);
  return new Date(Date.UTC(year, month - 1, day));
};

const formatDate = (value) => value.toISOString().slice(0, 10);

export const shiftAnchor = (anchor, period, amount) => {
  const date = parseDate(anchor);
  if (period === "day") date.setUTCDate(date.getUTCDate() + amount);
  if (period === "week") date.setUTCDate(date.getUTCDate() + amount * 7);
  if (period === "month")
    return formatDate(
      new Date(Date.UTC(date.getUTCFullYear(), date.getUTCMonth() + amount, 1)),
    );
  if (period === "year")
    return formatDate(new Date(Date.UTC(date.getUTCFullYear() + amount, 0, 1)));
  return formatDate(date);
};
