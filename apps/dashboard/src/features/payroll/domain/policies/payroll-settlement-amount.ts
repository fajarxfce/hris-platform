/** Settlement totals can contain several transfers; each new transfer has its own lower ceiling. */
export function payrollSettlementAmountMinor(value: string): bigint | null {
  if (!/^(?:0|[1-9]\d{0,13})(?:\.\d{1,2})?$/u.test(value)) return null;
  const [whole = "0", fraction = ""] = value.split(".");
  return BigInt(whole) * 100n + BigInt(fraction.padEnd(2, "0"));
}
