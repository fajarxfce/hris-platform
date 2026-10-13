/** Exact IDR minor units; bounded input excludes exponents and locale separators. */
export function expenseAmountMinor(value: string): bigint | null {
  if (!/^(?:0|[1-9]\d{0,11})(?:\.\d{1,2})?$/u.test(value)) return null;
  const [whole = "0", fraction = ""] = value.split(".");
  return BigInt(whole) * 100n + BigInt(fraction.padEnd(2, "0"));
}
