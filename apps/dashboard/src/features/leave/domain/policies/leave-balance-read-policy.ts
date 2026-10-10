export const isLeaveBalanceYear = (year: string): boolean =>
  /^\d{4}$/u.test(year) && Number(year) >= 1900 && Number(year) <= 2200;
