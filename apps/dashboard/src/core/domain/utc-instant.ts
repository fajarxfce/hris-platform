/** Parse UTC ISO timestamps at PostgreSQL microsecond precision, retaining dates before Unix epoch. */
export function utcInstantMicroseconds(value: string): bigint | null {
  const match = /^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})(?:\.(\d{1,9}))?Z$/u.exec(value);
  if (!match) return null;
  const date = new Date(`${match[1]}Z`);
  if (!Number.isFinite(date.getTime()) || date.toISOString().slice(0, 19) !== match[1]) return null;
  return BigInt(date.getTime()) * 1_000n + BigInt((match[2] ?? "").padEnd(9, "0")) / 1_000n;
}
