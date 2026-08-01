export function monthEnd(month) {
  const [year, monthNumber] = month.split("-").map(Number);
  const day = new Date(year, monthNumber, 0).getDate();
  return `${month}-${String(day).padStart(2, "0")}`;
}

export function localToday(now = new Date()) {
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, "0");
  const day = String(now.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}
