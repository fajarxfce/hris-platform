import type { Locale } from "../../../../core/presentation/i18n/messages";
import { formatIdrAmount } from "../../../../core/presentation/money/format-idr-amount";

export function expenseAmountView(amount: string, locale: Locale): string {
  return formatIdrAmount(amount, locale);
}
