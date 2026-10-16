import type { AccountId } from "../../../../core/domain/identifiers";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { CompanyAccess } from "../../domain/entities/session";
import type { IdentityAdministrationUseCases } from "./identity-administration-use-cases";
export type RoleTemplateScreenProps = {
  accountId: AccountId;
  access: CompanyAccess;
  actions: IdentityAdministrationUseCases;
  companyName: string;
  locale: Locale;
  timezone: string;
  nextIdentifier: () => string;
};
