import { Text } from "@fluentui/react-components";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import { approvalDelegationMessages } from "../i18n/approval-delegation-messages";
import { approvalMessages } from "../i18n/approval-messages";
import type { approvalDelegationsView } from "../models/approval-delegation-view";
import type { ApprovalDelegationsState } from "../models/approval-delegations-state";

export function ApprovalDelegationsPage({
  state,
  rows,
  companyName,
  timezone,
  locale,
  firstPage,
  createTo,
  onRefresh,
  onFirst,
  onNext,
  onOpen,
}: {
  state: ApprovalDelegationsState;
  rows: ReturnType<typeof approvalDelegationsView>;
  companyName: string;
  timezone: string;
  locale: Locale;
  firstPage: boolean;
  createTo: string | null;
  onRefresh: () => void;
  onFirst: () => void;
  onNext: () => void;
  onOpen: (id: string) => void;
}) {
  const text = approvalDelegationMessages(locale);
  const approval = approvalMessages(locale);
  return (
    <section aria-busy={state.stage === "loading"}>
      <AppPageHeader
        title={text.title}
        context={companyName}
        actions={
          <>
            {createTo && <Link to={createTo}>{text.create}</Link>}
            <AppButton disabled={state.stage === "loading"} onClick={onRefresh}>
              {messages(locale).refresh}
            </AppButton>
          </>
        }
      />
      <AppFailure failure={state.failure} locale={locale} />
      <div className="app-report-content">
        <p>
          {text.scope} {text.zoneHint}: {timezone}.
        </p>
        {state.stage === "loading" && <AppLoading label={messages(locale).loading} />}
        {state.page &&
          (rows.length === 0 ? (
            <Text role="status">{text.empty}</Text>
          ) : (
            <AppResourceTable
              title={text.title}
              columns={[
                { id: "kind", label: approval.kind },
                { id: "from", label: text.from },
                { id: "to", label: text.to },
                { id: "start", label: text.starts },
                { id: "end", label: text.ends },
                { id: "configuration", label: text.setting },
              ]}
              rows={rows}
              action={{ label: approval.view, onOpen }}
            />
          ))}
        <fieldset className="app-pagination" aria-label={text.pages}>
          <AppButton onClick={onFirst} disabled={firstPage || state.stage === "loading"}>
            {approval.first}
          </AppButton>
          <AppButton
            onClick={onNext}
            disabled={state.page?.nextCursor == null || state.stage === "loading"}
          >
            {approval.next}
          </AppButton>
        </fieldset>
      </div>
    </section>
  );
}
