import { MessageBar, MessageBarBody } from "@fluentui/react-components";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppConfirmationDialog } from "../../../../core/presentation/components/app-confirmation-dialog";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppMultiSelect } from "../../../../core/presentation/components/app-multi-select";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppSelect } from "../../../../core/presentation/components/app-select";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import { failureMessage } from "../../../../core/presentation/i18n/failure-message";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import { overtimeStatuses } from "../../../workforce/domain/entities/overtime-request";
import type { useReportFilterForm } from "../controllers/use-report-filter-form";
import { reportCatalogueMessages } from "../i18n/report-catalogue-messages";
import { reportingMessages } from "../i18n/reporting-messages";
import { savedReportFilterMessages } from "../i18n/saved-report-filter-messages";
import type { ReportFilterEditorState } from "../models/report-filter-editor-state";

export function ReportFilterEditorPage({
  state,
  form,
  creating,
  companyName,
  locale,
  backTo,
  openTo,
  onRetry,
  onReviewDelete,
  onCancelDelete,
  onConfirmDelete,
}: {
  state: ReportFilterEditorState;
  form: ReturnType<typeof useReportFilterForm>;
  creating: boolean;
  companyName: string;
  locale: Locale;
  backTo: string;
  openTo: string | null;
  onRetry: () => void;
  onReviewDelete: () => void;
  onCancelDelete: () => void;
  onConfirmDelete: () => void;
}) {
  const text = savedReportFilterMessages(locale),
    shared = messages(locale),
    reports = reportCatalogueMessages(locale),
    reportText = reportingMessages(locale);
  return (
    <section
      className="app-report-content"
      aria-busy={state.stage === "loading" || state.stage === "saving"}
    >
      <AppPageHeader
        title={creating ? text.create : text.edit}
        context={`${companyName} / ${text.title}`}
        actions={<Link to={backTo}>{text.back}</Link>}
      />
      <AppFailure failure={state.failure} locale={locale} />
      {state.stage === "loading" && <AppLoading label={shared.loading} />}
      {state.stage === "unavailable" && (
        <AppButton onClick={form.refresh}>{shared.retry}</AppButton>
      )}
      {state.stage === "unconfirmed" && (
        <MessageBar layout="multiline" intent="warning" role="status">
          <MessageBarBody>
            {text.unconfirmed}
            <div className="app-reference">
              {text.operation}: {state.operationId}
            </div>
            <AppButton onClick={onRetry}>{text.retry}</AppButton>
          </MessageBarBody>
        </MessageBar>
      )}
      {state.stage === "completed" && (
        <MessageBar layout="multiline" intent="success" role="status">
          <MessageBarBody>
            {state.command === "delete" ? text.deleted : text.saved} {text.version}:{" "}
            {state.receipt?.version}.
          </MessageBarBody>
        </MessageBar>
      )}
      {!["loading", "unavailable", "completed"].includes(state.stage) && (
        <>
          {state.filter && (
            <div className="app-form-actions">
              <span>
                {text.version}: {state.filter.version}
              </span>
              {openTo ? <Link to={openTo}>{text.open}</Link> : <p>{text.unavailable}</p>}
            </div>
          )}
          <form className="app-editor-form" onSubmit={form.submit} noValidate>
            <fieldset className="app-report-filter-fields" disabled={!form.editable}>
              <AppTextField label={text.name} {...form.name} maxLength={80} required />
              <AppSelect
                label={reports.report}
                {...form.kind}
                onChange={(_event, data) => form.selectKind(data.value)}
              >
                {form.kinds.map((kind) => (
                  <option key={kind} value={kind}>
                    {reports[kind].title}
                  </option>
                ))}
              </AppSelect>
              <p>{reports[form.kind.value].description}</p>
              {form.kind.value === "HEADCOUNT" ? (
                <>
                  <AppTextField
                    label={text.asOf}
                    type="date"
                    min="1900-01-01"
                    max="2100-12-31"
                    {...form.asOf}
                    required
                  />
                  <AppMultiSelect
                    label={text.companies}
                    ref={form.companies.ref}
                    value={form.companies.value}
                    displayValue={form.companies.displayValue}
                    options={form.companies.options}
                    placeholder={text.chooseCompanies}
                    onChange={form.companies.onChange}
                    error={
                      form.companies.invalid
                        ? failureMessage(
                            { code: "invalid_report_companies", fields: {}, parameters: {} },
                            locale,
                          )
                        : undefined
                    }
                  />
                  <p>{text.groupHint}</p>
                </>
              ) : (
                <div className="app-editor-fields">
                  <AppTextField
                    label={text.from}
                    type={form.month ? "month" : "date"}
                    {...form.from}
                    required
                  />
                  <AppTextField
                    label={text.until}
                    type={form.month ? "month" : "date"}
                    {...form.until}
                    required
                  />
                </div>
              )}
              {form.kind.value === "OVERTIME" && (
                <AppSelect label={reportText.overtimeStatus} {...form.status}>
                  <option value="">{reportText.allStatuses}</option>
                  {overtimeStatuses.map((status) => (
                    <option key={status} value={status}>
                      {reportText[status]}
                    </option>
                  ))}
                </AppSelect>
              )}
            </fieldset>
            <div className="app-form-actions">
              <AppButton appearance="primary" type="submit" disabled={!form.canSave}>
                {text.save}
              </AppButton>
              {state.stage === "conflict" && (
                <AppButton onClick={form.refresh}>{text.reload}</AppButton>
              )}
              {state.filter && (
                <AppConfirmationDialog
                  open={state.stage === "confirming-delete"}
                  title={text.confirmDelete}
                  message={`${state.filter.name}. ${text.deleteMessage}`}
                  confirmLabel={text.remove}
                  dismissLabel={shared.cancel}
                  busy={false}
                  onConfirm={onConfirmDelete}
                  onDismiss={onCancelDelete}
                  trigger={
                    <AppButton disabled={!form.editable} onClick={onReviewDelete}>
                      {text.remove}
                    </AppButton>
                  }
                />
              )}
            </div>
          </form>
        </>
      )}
    </section>
  );
}
