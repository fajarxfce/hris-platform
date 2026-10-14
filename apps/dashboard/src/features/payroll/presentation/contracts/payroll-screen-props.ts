import type { AccountId } from "../../../../core/domain/identifiers";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PayrollUseCases } from "./payroll-use-cases";

export type PayrollScreenProps = Readonly<{
  accountId: AccountId;
  access: CompanyAccess;
  payroll: PayrollUseCases;
  companyName: string;
  timezone: string;
  locale: Locale;
  nextIdentifier: () => string;
}>;
