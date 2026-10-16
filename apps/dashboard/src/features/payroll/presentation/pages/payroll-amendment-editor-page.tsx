import { ArrowSync20Regular } from "@fluentui/react-icons";
import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppCommandBar } from "../../../../core/presentation/components/app-command-bar";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { AppTextArea } from "../../../../core/presentation/components/app-text-area";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { usePayrollAmendmentEditorForm } from "../controllers/use-payroll-amendment-editor-form";
import { payrollAmendmentEditorMessages } from "../i18n/payroll-amendment-editor-messages";
import { payrollAmendmentMessages } from "../i18n/payroll-amendment-messages";
import type { PayrollAmendmentEditorState } from "../models/payroll-amendment-editor-state";
import type { payrollAmendmentEditorView } from "../models/payroll-amendment-editor-view";
import { PayrollAmendmentAttendanceSection } from "./payroll-amendment-attendance-section";
export function PayrollAmendmentEditorPage(props: {
  state: PayrollAmendmentEditorState;
  view: ReturnType<typeof payrollAmendmentEditorView>;
  locale: Locale;
  companyName: string;
  backTo: string;
  savedTo: string | null;
  form: ReturnType<typeof usePayrollAmendmentEditorForm>;
  onMonth: (id: string) => void;
  onPreview: () => void;
  onAcceptSources: () => void;
  onRetry: () => void;
  children: ReactNode;
}) {
  const { state, view, form, locale } = props,
    text = payrollAmendmentEditorMessages(locale),
    read = payrollAmendmentMessages(locale);
  return (
    <section aria-busy={["loading", "previewing", "saving"].includes(state.stage)}>
      <AppPageHeader
        title={view?.title ?? text.editor}
        context={`${props.companyName} / ${text.editor}`}
        actions={<Link to={props.backTo}>{text.back}</Link>}
      />
      <AppCommandBar label={text.editor}>
        <AppButton
          appearance="subtle"
          icon={<ArrowSync20Regular />}
          disabled={["loading", "saving", "unconfirmed", "saved"].includes(state.stage)}
          onClick={form.refresh}
        >
          {text.reload}
        </AppButton>
      </AppCommandBar>
      <div className="app-report-content app-amendment-editor">
        <AppFailure failure={state.failure} locale={locale} />
        {state.stage === "loading" && <AppLoading label={messages(locale).loading} />}
        {state.stage === "saving" && <AppLoading label={text.saving} />}
        {state.stage === "unconfirmed" && (
          <div role="status">
            <p>{text.uncertain}</p>
            <p className="app-reference">
              {text.operation}: {state.operationId}
            </p>
            <AppButton onClick={props.onRetry}>{text.retry}</AppButton>
          </div>
        )}
        {state.stage === "saved" && (
          <p role="status">
            {text.saved} {read.revision}: {state.receipt?.version}.{" "}
            {props.savedTo && <Link to={props.savedTo}>{text.view}</Link>}
          </p>
        )}
        {view && state.context && (
          <>
            <AppPropertyList title={text.editor} items={view.properties} />
            <AppResourceTable
              title={text.sourcesRead}
              minWidth={930}
              columns={[
                { id: "month", label: text.month },
                { id: "employee", label: read.employee },
                { id: "current", label: text.current, numeric: true },
                { id: "observed", label: text.observed, numeric: true },
                { id: "amount", label: text.amount, numeric: true },
                { id: "changes", label: text.proposed },
              ]}
              rows={view.rows}
              action={{
                label: text.change,
                onOpen: props.onMonth,
                columnId: "month",
                disabled: !["editing", "previewing"].includes(state.stage),
              }}
            />
            {state.stage === "source-review" && (
              <div role="status">
                <p>{text.sourcesChanged}</p>
                <AppButton onClick={props.onAcceptSources}>{text.acceptSources}</AppButton>
              </div>
            )}
            {state.stage === "editing" && (
              <div className="app-form-actions">
                <AppButton onClick={props.onPreview}>{text.preview}</AppButton>
                <span className="app-muted">{text.previewHint}</span>
              </div>
            )}
            {state.stage === "previewing" && <AppLoading label={text.previewing} />}
            {state.preview && (
              <>
                <p role="status">{text.previewReady}</p>
                <AppPropertyList title={text.totals} items={view.totals} />
                <AppResourceTable
                  title={text.comparison}
                  minWidth={1080}
                  columns={[
                    { id: "month", label: text.month },
                    {
                      id: "gross",
                      label: `${read.taxableGross} · ${text.difference}`,
                      numeric: true,
                    },
                    { id: "tax", label: `${read.withheld} · ${text.difference}`, numeric: true },
                    { id: "before", label: `${text.basis} · ${text.amount}`, numeric: true },
                    { id: "after", label: `${text.proposed} · ${text.amount}`, numeric: true },
                    { id: "difference", label: text.difference, numeric: true },
                  ]}
                  rows={view.comparison}
                />
                {view.attendance.map((row) => (
                  <PayrollAmendmentAttendanceSection key={row.id} view={row.view} />
                ))}
              </>
            )}
            <form className="app-editor-form" onSubmit={form.submit} noValidate>
              <AppTextArea
                label={text.reason}
                {...form.reason}
                required
                maxLength={1000}
                rows={2}
                readOnly={state.stage !== "editing"}
                error={state.failure?.fields.reason ? text.requiredReason : undefined}
              />
              <div className="app-form-actions">
                <AppButton
                  type="submit"
                  appearance="primary"
                  disabled={state.stage !== "editing" || !state.preview}
                >
                  {text.save}
                </AppButton>
              </div>
            </form>
          </>
        )}
      </div>
      {props.children}
    </section>
  );
}
