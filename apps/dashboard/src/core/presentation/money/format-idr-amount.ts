import type { Locale } from "../i18n/messages";

/** Format validated decimal amounts without converting money to a binary float. */
export function formatIdrAmount(amount: string, locale: Locale): string {
  const [whole = "0", fraction = ""] = amount.split(".");
  const integer = BigInt(whole);
  const negativeZero = whole.startsWith("-") && integer === 0n;
  return new Intl.NumberFormat(locale, {
    style: "currency",
    currency: "IDR",
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })
    .formatToParts(negativeZero ? -1n : integer)
    .map((part) => {
      if (part.type === "fraction") return fraction.padEnd(2, "0");
      if (negativeZero && part.type === "integer") return "0";
      return part.value;
    })
    .join("");
}
