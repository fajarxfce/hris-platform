/** Compare at database precision, also accepting clock instants with nanoseconds. Date loses microsecond ties. */
export function auditInstantMicros(value: string): bigint | null {
  const match = /^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})(?:\.(\d{1,9}))?Z$/u.exec(value);
  if (!match || value < "1900-01-01") return null;
  const seconds = `${match[1]}Z`;
  const date = new Date(seconds);
  if (!Number.isFinite(date.getTime()) || date.toISOString().slice(0, 19) !== match[1]) return null;
  return BigInt(date.getTime()) * 1_000n + BigInt((match[2] ?? "").padEnd(9, "0")) / 1_000n;
}
