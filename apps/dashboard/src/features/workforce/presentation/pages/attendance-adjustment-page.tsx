import { Text } from "@fluentui/react-components";
import { ArrowClockwise20Regular } from "@fluentui/react-icons";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppCheckbox } from "../../../../core/presentation/components/app-checkbox";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { AppSelect } from "../../../../core/presentation/components/app-select";
import { AppTextArea } from "../../../../core/presentation/components/app-text-area";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { useAttendanceAdjustmentForm } from "../controllers/use-attendance-adjustment-form";
import { attendanceAdjustmentMessages } from "../i18n/attendance-adjustment-messages";
import { attendanceMessages } from "../i18n/attendance-messages";
import type { AttendanceAdjustmentState } from "../models/attendance-adjustment-state";
import type { attendanceAdjustmentReviewView } from "../models/attendance-adjustment-view";

export function AttendanceAdjustmentPage(props: {
  state: AttendanceAdjustmentState;
  form: ReturnType<typeof useAttendanceAdjustmentForm>;
  view: ReturnType<typeof attendanceAdjustmentReviewView> | null;
  locale: Locale;
  companyName: string;
  timezone: string;
  formId: string;
  backTo: string;
  historyTo: string;
  receiptTo: string | null;
  onRetry: () => void;
}) {
  const { state, form, view, locale } = props,
    text = attendanceAdjustmentMessages(locale),
    attendance = attendanceMessages(locale);
  return (
    <section
      className="app-report-content"
      aria-busy={state.stage === "loading" || state.stage === "saving"}
    >
      <AppPageHeader
        title={text.title}
        context={`${props.companyName} / ${state.review?.employee.legalName ?? text.review}`}
        actions={<Link to={props.backTo}>{text.back}</Link>}
      />
      <div className="app-command-bar">
        <Link to={props.historyTo}>{text.history}</Link>
        <AppButton
          appearance="subtle"
          icon={<ArrowClockwise20Regular />}
          disabled={["loading", "saving", "unconfirmed", "saved"].includes(state.stage)}
          onClick={form.reload}
        >
          {text.reload}
        </AppButton>
      </div>
      <AppFailure failure={state.failure} locale={locale} />
      {state.stage === "loading" && <AppLoading label={messages(locale).loading} />}
      {state.stage === "saving" && <AppLoading label={text.saving} />}
      {state.stage === "saved" && (
        <div className="app-form-feedback" role="status">
          <Text>{text.saved}</Text>
          {props.receiptTo && <Link to={props.receiptTo}>{text.savedDetails}</Link>}
        </div>
      )}
      {state.stage === "unconfirmed" && (
        <div className="app-form-feedback">
          <Text role="status">{text.uncertain}</Text>
          <AppButton onClick={props.onRetry}>{text.retry}</AppButton>
        </div>
      )}
      {state.stage === "conflict" && <Text role="status">{text.conflict}</Text>}
      {view && state.review && (
        <>
          <AppPropertyList title={text.review} items={view.properties} />
          <AppResourceTable title={text.current} columns={view.columns} rows={view.rows} />
          <Text className="app-muted">{text.hint}</Text>
          {!state.review.canCorrect && <Text role="status">{text.readOnly}</Text>}
          {state.review.attendance.pendingCount > 0 && !state.review.canReviewEvidence && (
            <Text role="status">{text.cannotVerify}</Text>
          )}
          <form id={props.formId} className="app-report-content" onSubmit={form.submit}>
            <section className="app-resource-panel" aria-label={text.evidence}>
              <h2>{text.evidence}</h2>
              {state.review.attendance.pendingCount > 0 && (
                <p className="app-muted">{text.pending}</p>
              )}
              {view.entries.length === 0 && <p>{text.noEvidence}</p>}
              {view.entries.map((entry) => (
                <div key={entry.id} className="app-adjustment-evidence">
                  <details>
                    <summary>
                      {entry.title} · {entry.status}
                    </summary>
                    <AppPropertyList title={entry.kind} items={entry.properties} />
                  </details>
                  {entry.pending && (
                    <AppSelect
                      label={entry.decisionLabel}
                      value={form.decisions[entry.id] ?? ""}
                      onChange={(event) => form.decide(entry.id, event.target.value)}
                      disabled={!form.editable}
                      required
                    >
                      <option value="">{text.choose}</option>
                      <option value="ACCEPT">{attendance.ACCEPT}</option>
                      <option value="REJECT">{attendance.REJECT}</option>
                    </AppSelect>
                  )}
                </div>
              ))}
            </section>
            {state.review.canCorrect && (
              <section className="app-resource-panel" aria-label={text.correction}>
                <h2>{text.correction}</h2>
                <p className="app-muted">
                  {attendance.timezone}: {props.timezone}
                </p>
                <div className="app-report-content">
                  <AppCheckbox
                    label={attendance.absence}
                    checked={form.absence.value}
                    onChange={(_, data) => form.absence.onChange(data.checked === true)}
                    disabled={!form.editable}
                  />
                  <div className="app-editor-fields">
                    <AppTextField
                      label={attendance.clockIn}
                      type="datetime-local"
                      step={60}
                      {...form.clockIn}
                      disabled={!form.editable || form.absence.value}
                      required={!form.absence.value}
                    />
                    <AppTextField
                      label={attendance.clockOut}
                      type="datetime-local"
                      step={60}
                      {...form.clockOut}
                      disabled={!form.editable || form.absence.value}
                      required={!form.absence.value}
                    />
                    <AppTextField
                      label={attendance.breakMinutes}
                      type="number"
                      min={0}
                      max={1439}
                      step={1}
                      {...form.breakMinutes}
                      disabled={!form.editable || form.absence.value}
                    />
                  </div>
                  <AppTextArea
                    label={attendance.reason}
                    {...form.reason}
                    required
                    maxLength={1000}
                    readOnly={!form.editable}
                  />
                </div>
              </section>
            )}
            {form.invalid && <p role="alert">{text.invalid}</p>}
            {state.review.canCorrect && (
              <div className="app-form-actions">
                <AppButton type="submit" appearance="primary" disabled={!form.editable}>
                  {text.save}
                </AppButton>
              </div>
            )}
          </form>
        </>
      )}
    </section>
  );
}
