import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import { companyMemberMessages } from "../i18n/company-member-messages";
import type { companyMemberRows } from "../models/company-member-view";
import type { CompanyMembersState } from "../models/company-members-state";

export function CompanyMembersPage({
  state,
  rows,
  locale,
  companyName,
  firstPage,
  onRefresh,
  onFirst,
  onNext,
  onOpen,
}: {
  state: CompanyMembersState;
  rows: ReturnType<typeof companyMemberRows>;
  locale: Locale;
  companyName: string;
  firstPage: boolean;
  onRefresh: () => void;
  onFirst: () => void;
  onNext: () => void;
  onOpen: (id: string) => void;
}) {
  const text = companyMemberMessages(locale);
  return (
    <section className="app-report-content" aria-busy={state.stage === "loading"}>
      <AppPageHeader
        title={text.title}
        context={companyName}
        actions={
          <AppButton disabled={state.stage === "loading"} onClick={onRefresh}>
            {messages(locale).refresh}
          </AppButton>
        }
      />
      <AppFailure failure={state.failure} locale={locale} />
      {state.stage === "loading" && <AppLoading label={messages(locale).loading} />}
      {state.page &&
        (rows.length === 0 ? (
          <p role="status">{text.empty}</p>
        ) : (
          <AppResourceTable
            title={text.directory}
            rows={rows}
            columns={[
              { id: "name", label: text.name },
              { id: "email", label: text.email },
              { id: "account", label: text.account },
              { id: "membership", label: text.membership },
            ]}
            action={{ label: text.view, onOpen }}
          />
        ))}
      <fieldset className="app-pagination" aria-label={text.page}>
        <AppButton disabled={firstPage || state.stage === "loading"} onClick={onFirst}>
          {text.first}
        </AppButton>
        <AppButton disabled={!state.page?.nextCursor || state.stage === "loading"} onClick={onNext}>
          {text.next}
        </AppButton>
      </fieldset>
    </section>
  );
}
