export function canReadHeadcount(permissions: readonly string[]): boolean {
  return permissions.includes("reports.read") && permissions.includes("people.read");
}

export function isHeadcountDate(value: string): boolean {
  if (!/^(?:19\d{2}|20\d{2}|2100)-\d{2}-\d{2}$/u.test(value)) return false;
  const date = new Date(`${value}T00:00:00.000Z`);
  return Number.isFinite(date.getTime()) && date.toISOString().slice(0, 10) === value;
}

export function isHeadcountCompanies(values: readonly string[]): boolean {
  return (
    values.length >= 1 &&
    values.length <= 32 &&
    new Set(values.map((id) => id.toLowerCase())).size === values.length &&
    values.every((id) =>
      /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/iu.test(id),
    )
  );
}
