import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { AppSelect } from "../../../../core/presentation/components/app-select";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import { approvalKinds } from "../../domain/entities/approval-request";
import type { useApprovalTemplateFilters } from "../controllers/use-approval-template-filters";
import { approvalMessages } from "../i18n/approval-messages";
import { approvalTemplateMessages } from "../i18n/approval-template-messages";
import type { approvalTemplatesView } from "../models/approval-template-view";
import type { ApprovalTemplatesState } from "../models/approval-templates-state";

export function ApprovalTemplatesPage({
  state,
  rows,
  filters,
  companyName,
  locale,
  firstPage,
  createTo,
  onRefresh,
  onFirst,
  onNext,
  onOpen,
}: {
  state: ApprovalTemplatesState;
  rows: ReturnType<typeof approvalTemplatesView>;
  filters: ReturnType<typeof useApprovalTemplateFilters>;
  companyName: string;
  locale: Locale;
  firstPage: boolean;
  createTo: string | null;
  onRefresh: () => void;
  onFirst: () => void;
  onNext: () => void;
  onOpen: (id: string) => void;
}) {
  const text = approvalTemplateMessages(locale);
  const approval = approvalMessages(locale);
  return (
    <section aria-busy={state.stage === "loading"}>
      <AppPageHeader
        title={text.title}
        context={companyName}
        actions={
          <>
            {createTo && <Link to={createTo}>{text.create}</Link>}
            <AppButton onClick={onRefresh} disabled={state.stage === "loading"}>
              {messages(locale).refresh}
            </AppButton>
          </>
        }
      />
      <AppFailure failure={state.failure} locale={locale} />
      <div className="app-report-content">
        <form className="app-directory-filters" onSubmit={filters.submit} noValidate>
          <AppSelect label={approval.kind} {...filters.kind}>
            {approvalKinds.map((kind) => (
              <option key={kind} value={kind}>
                {approval[kind]}
              </option>
            ))}
          </AppSelect>
          <AppTextField
            label={text.asOf}
            {...filters.asOf}
            type="date"
            min="1900-01-01"
            max="2200-12-31"
            required
          />
          <AppButton type="submit" appearance="primary">
            {text.apply}
          </AppButton>
        </form>
        {state.stage === "loading" && <AppLoading label={messages(locale).loading} />}
        {state.page &&
          (rows.length === 0 ? (
            <p role="status">{text.empty}</p>
          ) : (
            <AppResourceTable
              title={text.title}
              columns={[
                { id: "name", label: text.name },
                { id: "active", label: approval.status },
                { id: "effective", label: text.effectiveFrom },
                { id: "revision", label: text.revision },
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
            disabled={!state.page?.nextCursor || state.stage === "loading"}
          >
            {approval.next}
          </AppButton>
        </fieldset>
      </div>
    </section>
  );
}
