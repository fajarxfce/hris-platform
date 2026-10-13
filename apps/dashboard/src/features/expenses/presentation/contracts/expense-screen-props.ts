import type { AccountId } from "../../../../core/domain/identifiers";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { ExpenseUseCases } from "./expense-use-cases";
export type ExpenseScreenProps = Readonly<{
  accountId: AccountId;
  access: CompanyAccess;
  expense: ExpenseUseCases;
  companyName: string;
  timezone: string;
  locale: Locale;
}>;
