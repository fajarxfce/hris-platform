import type { AccountId } from "../../../../core/domain/identifiers";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { WorkforceUseCases } from "./workforce-use-cases";

export type WorkforceScreenProps = Readonly<{
  accountId: AccountId;
  access: CompanyAccess;
  workforce: WorkforceUseCases;
  companyName: string;
  timezone: string;
  locale: Locale;
  nextIdentifier: () => string;
}>;
