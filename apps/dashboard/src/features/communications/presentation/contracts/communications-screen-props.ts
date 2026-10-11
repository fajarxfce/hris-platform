import type { AccountId } from "../../../../core/domain/identifiers";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { CommunicationsUseCases } from "./communications-use-cases";

export type CommunicationsScreenProps = Readonly<{
  accountId: AccountId;
  access: CompanyAccess;
  communications: CommunicationsUseCases;
  companyName: string;
  timezone: string;
  locale: Locale;
  nextIdentifier: () => string;
}>;
