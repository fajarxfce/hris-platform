import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import { approvalDelegationMessages } from "../i18n/approval-delegation-messages";
import type { ApprovalDelegationState } from "../models/approval-delegation-state";
import type { approvalDelegationView } from "../models/approval-delegation-view";

export function ApprovalDelegationPage({
  state,
  properties,
  companyName,
  locale,
  backTo,
  editTo,
  onRefresh,
}: {
  state: ApprovalDelegationState;
  properties: ReturnType<typeof approvalDelegationView> | null;
  companyName: string;
  locale: Locale;
  backTo: string;
  editTo: string | null;
  onRefresh: () => void;
}) {
  const text = approvalDelegationMessages(locale);
  return (
    <section aria-busy={state.stage === "loading"}>
      <AppPageHeader
        title={text.details}
        context={companyName}
        actions={
          <>
            <Link to={backTo}>{text.back}</Link>
            {editTo && <Link to={editTo}>{text.edit}</Link>}
            <AppButton disabled={state.stage === "loading"} onClick={onRefresh}>
              {messages(locale).refresh}
            </AppButton>
          </>
        }
      />
      <AppFailure failure={state.failure} locale={locale} />
      <div className="app-report-content">
        {state.stage === "loading" && <AppLoading label={messages(locale).loading} />}
        {properties && <AppPropertyList title={text.summary} items={properties} />}
      </div>
    </section>
  );
}
