export const payrollIdentifier = (index: number, company = 0): string =>
  `ba00000${company}-0000-4000-8000-${String(index).padStart(12, "0")}`;
