import type { AccountId } from "../../../../core/domain/identifiers";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LifecycleUseCases } from "./lifecycle-use-cases";
export type OffboardingScheduleScreenProps = Readonly<{
  accountId: AccountId;
  access: CompanyAccess;
  lifecycle: LifecycleUseCases;
  companyName: string;
  locale: Locale;
  nextIdentifier: () => string;
}>;
