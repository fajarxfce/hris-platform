import type { AccountId } from "../../../../core/domain/identifiers";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { LoadEmployee } from "../../../people/domain/usecases/load-employee";
import type { LifecycleUseCases } from "./lifecycle-use-cases";
export type OnboardingScheduleScreenProps = Readonly<{
  accountId: AccountId;
  access: CompanyAccess;
  loadEmployee: Pick<LoadEmployee, "execute">;
  lifecycle: LifecycleUseCases;
  companyName: string;
  timezone: string;
  locale: Locale;
  nextIdentifier: () => string;
}>;
