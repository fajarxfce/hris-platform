import type { AccountId } from "../../../../core/domain/identifiers";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { PlatformAccess } from "../../domain/entities/platform-access";
import type { CompanyAccess } from "../../domain/entities/session";
import type { IdentityAdministrationUseCases } from "./identity-administration-use-cases";
export type CompanyMembershipScreenProps = {
  accountId: AccountId;
  access: CompanyAccess;
  platform: PlatformAccess;
  actions: IdentityAdministrationUseCases;
  companyName: string;
  locale: Locale;
  nextIdentifier: () => string;
};
