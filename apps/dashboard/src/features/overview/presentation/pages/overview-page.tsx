import { Text } from "@fluentui/react-components";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { CompanyMembership } from "../../../identity/domain/entities/session";

export function OverviewPage({
  company,
  locale,
}: {
  company: CompanyMembership | null;
  locale: Locale;
}) {
  const text = messages(locale);
  return (
    <>
      <AppPageHeader title={text.overview} />
      {company ? (
        <section className="app-resource-panel" aria-label={text.company}>
          <h2>{company.name}</h2>
          <dl className="app-property-list">
            <dt>{text.code}</dt>
            <dd>{company.code}</dd>
            <dt>{text.timezone}</dt>
            <dd>{company.timezone}</dd>
          </dl>
        </section>
      ) : (
        <section className="app-resource-panel">
          <h2>{text.noCompanies}</h2>
          <Text>{text.noCompaniesDescription}</Text>
        </section>
      )}
    </>
  );
}
