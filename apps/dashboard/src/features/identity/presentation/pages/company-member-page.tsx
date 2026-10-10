import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import { companyMemberMessages } from "../i18n/company-member-messages";
import type { CompanyMemberState } from "../models/company-member-state";
import type { companyMemberProperties } from "../models/company-member-view";

export function CompanyMemberPage({
  state,
  properties,
  locale,
  companyName,
  backTo,
  onRefresh,
}: {
  state: CompanyMemberState;
  properties: ReturnType<typeof companyMemberProperties>;
  locale: Locale;
  companyName: string;
  backTo: string;
  onRefresh: () => void;
}) {
  const text = companyMemberMessages(locale);
  return (
    <section className="app-report-content" aria-busy={state.stage === "loading"}>
      <AppPageHeader
        title={state.grant?.member.displayName ?? text.details}
        context={`${companyName} / ${text.title}`}
        actions={
          <>
            <Link to={backTo}>{text.back}</Link>
            <AppButton disabled={state.stage === "loading"} onClick={onRefresh}>
              {messages(locale).refresh}
            </AppButton>
          </>
        }
      />
      <AppFailure failure={state.failure} locale={locale} />
      {state.stage === "loading" && <AppLoading label={messages(locale).loading} />}
      {state.grant && (
        <>
          <AppPropertyList title={text.details} items={properties} />
          <section className="app-panel" aria-label={text.roles}>
            <h2>{text.roles}</h2>
            {state.grant.roleTemplates.length === 0 && <p>{text.none}</p>}
            {state.grant.roleTemplates.map((role) => (
              <AppPropertyList
                key={role.id}
                title={`${role.name} (${role.code})`}
                items={[
                  { label: text.roleVersion, value: String(role.version) },
                  { label: text.effective, value: role.permissions.join(", ") || text.none },
                ]}
              />
            ))}
          </section>
        </>
      )}
    </section>
  );
}
