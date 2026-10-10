import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import { approvalMessages } from "../i18n/approval-messages";
import { approvalTemplateMessages } from "../i18n/approval-template-messages";
import type { ApprovalTemplateState } from "../models/approval-template-state";
import type { approvalTemplateView } from "../models/approval-template-view";

export function ApprovalTemplatePage({
  state,
  view,
  companyName,
  locale,
  backTo,
  editTo,
  previousTo,
  nextTo,
  latestTo,
  onRefresh,
}: {
  state: ApprovalTemplateState;
  view: ReturnType<typeof approvalTemplateView> | null;
  companyName: string;
  locale: Locale;
  backTo: string;
  editTo: string | null;
  previousTo: string | null;
  nextTo: string | null;
  latestTo: string | null;
  onRefresh: () => void;
}) {
  const text = approvalTemplateMessages(locale);
  const approval = approvalMessages(locale);
  return (
    <section aria-busy={state.stage === "loading"}>
      <AppPageHeader
        title={state.template?.name ?? text.details}
        context={`${companyName} / ${text.title}`}
        actions={
          <>
            <Link to={backTo}>{text.back}</Link>
            {editTo && <Link to={editTo}>{text.edit}</Link>}
            <AppButton onClick={onRefresh} disabled={state.stage === "loading"}>
              {messages(locale).refresh}
            </AppButton>
          </>
        }
      />
      <AppFailure failure={state.failure} locale={locale} />
      {state.stage === "loading" && <AppLoading label={messages(locale).loading} />}
      <div className="app-report-content">
        {view && (
          <>
            <p>{text.revisionHint}</p>
            <AppPropertyList title={text.overview} items={view.properties} />
            <AppResourceTable
              title={approval.stages}
              columns={[
                { id: "stage", label: approval.step },
                { id: "assignment", label: text.assignment },
                { id: "accounts", label: approval.assignees },
              ]}
              rows={view.stages}
            />
          </>
        )}
        <nav className="app-form-actions" aria-label={text.revision}>
          {previousTo && <Link to={previousTo}>{text.previous}</Link>}
          {nextTo && <Link to={nextTo}>{text.nextRevision}</Link>}
          {latestTo && <Link to={latestTo}>{text.latest}</Link>}
        </nav>
      </div>
    </section>
  );
}
