import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { PlatformAccess } from "../../domain/entities/platform-access";
import type { IdentityAdministrationUseCases } from "./identity-administration-use-cases";

export type ManagedAccountScreenProps = Readonly<{
  access: PlatformAccess;
  actions: IdentityAdministrationUseCases;
  locale: Locale;
  nextIdentifier: () => string;
}>;
