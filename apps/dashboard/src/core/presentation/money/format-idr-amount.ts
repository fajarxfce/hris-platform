import type { Locale } from "../i18n/messages";

/** Format validated decimal amounts without converting money to a binary float. */
export function formatIdrAmount(amount: string, locale: Locale): string {
  const [whole = "0", fraction = ""] = amount.split(".");
  return new Intl.NumberFormat(locale, {
    style: "currency",
    currency: "IDR",
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })
    .formatToParts(BigInt(whole))
    .map((part) => (part.type === "fraction" ? fraction.padEnd(2, "0") : part.value))
    .join("");
}
