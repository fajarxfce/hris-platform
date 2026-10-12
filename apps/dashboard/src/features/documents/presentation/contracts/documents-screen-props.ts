import type { AccountId } from "../../../../core/domain/identifiers";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { DocumentsUseCases } from "./documents-use-cases";

export type DocumentsScreenProps = Readonly<{
  accountId: AccountId;
  access: CompanyAccess;
  documents: DocumentsUseCases;
  companyName: string;
  timezone: string;
  locale: Locale;
}>;
