import {
  Field,
  MessageBar,
  MessageBarBody,
  useRestoreFocusTarget,
} from "@fluentui/react-components";
import {
  Add20Regular,
  CalendarEdit20Regular,
  Dismiss20Regular,
  PersonSearch20Regular,
  Send20Regular,
} from "@fluentui/react-icons";
import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppCommandBar } from "../../../../core/presentation/components/app-command-bar";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { AppSelect } from "../../../../core/presentation/components/app-select";
import { AppTextArea } from "../../../../core/presentation/components/app-text-area";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { leavePortions } from "../../domain/entities/leave-day";
import type { useLeaveCreationForm } from "../controllers/use-leave-creation-form";
import { leaveCreationMessages } from "../i18n/leave-creation-messages";
import { leaveMessages } from "../i18n/leave-messages";
import type { LeaveCreationState } from "../models/leave-creation-state";
import type { leaveCreationReviewView } from "../models/leave-creation-view";

export function LeaveCreationPage(props: {
  formId: string;
  state: LeaveCreationState;
  form: ReturnType<typeof useLeaveCreationForm>;
  view: ReturnType<typeof leaveCreationReviewView> | null;
  locale: Locale;
  companyName: string;
  backTo: string;
  savedTo: string | null;
  picker: ReactNode;
  onRetry: () => void;
  onRemoveEvidence: (id: string) => void;
}) {
  const { state, form, view, locale } = props,
    text = leaveCreationMessages(locale),
    leave = leaveMessages(locale),
    restore = useRestoreFocusTarget();
  return (
    <section
      className="app-leave-creation"
      aria-busy={state.stage === "previewing" || state.stage === "submitting"}
    >
      <AppPageHeader
        title={text.title}
        context={props.companyName}
        actions={<Link to={props.backTo}>{text.back}</Link>}
      />
      <AppCommandBar label={text.actions}>
        {state.stage === "unconfirmed" ? (
          <AppButton appearance="primary" onClick={props.onRetry}>
            {text.retry}
          </AppButton>
        ) : (
          state.stage !== "saved" && (
            <AppButton
              appearance="primary"
              type="submit"
              form={props.formId}
              icon={form.canSubmit ? <Send20Regular /> : <CalendarEdit20Regular />}
              disabled={!form.canPreview && !form.canSubmit}
            >
              {state.stage === "submitting"
                ? text.submitting
                : form.canSubmit
                  ? text.submit
                  : text.review}
            </AppButton>
          )
        )}
        {state.stage === "saved" && props.savedTo && <Link to={props.savedTo}>{text.view}</Link>}
      </AppCommandBar>
      <div className="app-report-content">
        <AppFailure failure={state.failure} locale={locale} />
        {state.stage === "unconfirmed" && (
          <MessageBar intent="warning" layout="multiline" role="status">
            <MessageBarBody>{text.unconfirmed}</MessageBarBody>
          </MessageBar>
        )}
        {state.stage === "saved" && (
          <MessageBar intent="success" role="status">
            <MessageBarBody>{text.submitted}</MessageBarBody>
          </MessageBar>
        )}
        {state.stage === "submitting" && <AppLoading label={text.submitting} />}
        {state.stage === "unavailable" && (
          <div>
            <AppButton
              appearance="subtle"
              onClick={form.openEmployees}
              disabled={!form.canChooseEmployee}
            >
              {text.chooseEmployee}
            </AppButton>
          </div>
        )}
        {form.showForm && (
          <div className="app-leave-creation-layout">
            <form
              id={props.formId}
              className="app-editor-form"
              aria-label={text.title}
              onSubmit={form.submit}
              noValidate
            >
              <fieldset className="app-editor-section app-report-content">
                <legend>{text.details}</legend>
                <Field label={text.employee} required>
                  <div className="app-leave-reference">
                    {state.employee && (
                      <span>
                        <strong>{state.employee.legalName}</strong>
                        <span className="app-muted"> · {state.employee.employeeNumber}</span>
                      </span>
                    )}
                    <AppButton
                      {...restore}
                      appearance="subtle"
                      icon={<PersonSearch20Regular />}
                      onClick={form.openEmployees}
                      disabled={!form.canChooseEmployee}
                    >
                      {state.employee ? text.changeEmployee : text.chooseEmployee}
                    </AppButton>
                  </div>
                </Field>
                <div className="app-editor-fields">
                  <AppTextField
                    label={text.from}
                    type="date"
                    min="1900-01-01"
                    max="2200-12-31"
                    required
                    {...form.from}
                    readOnly={!form.editable}
                  />
                  <AppTextField
                    label={text.until}
                    type="date"
                    min={form.from.value}
                    max="2200-12-31"
                    required
                    {...form.until}
                    readOnly={!form.editable}
                  />
                </div>
                {form.invalidRange && (
                  <MessageBar intent="error" role="alert">
                    <MessageBarBody>{text.invalidRange}</MessageBarBody>
                  </MessageBar>
                )}
                <Field label={text.type} required>
                  <div className="app-leave-reference">
                    {state.type && (
                      <span>
                        {state.type.name} <span className="app-muted">· {state.type.code}</span>
                      </span>
                    )}
                    <AppButton
                      {...restore}
                      appearance="subtle"
                      onClick={form.openTypes}
                      disabled={!form.canChooseType}
                    >
                      {text.chooseType}
                    </AppButton>
                  </div>
                </Field>
                <div className="app-editor-fields">
                  <AppSelect
                    label={form.differentDates ? text.firstDay : text.dayLength}
                    {...form.first}
                    disabled={!form.editable || !state.type?.allowPartialDays}
                  >
                    {leavePortions.map((part) => (
                      <option key={part} value={part}>
                        {leave[part]}
                      </option>
                    ))}
                  </AppSelect>
                  {form.differentDates && (
                    <AppSelect
                      label={text.lastDay}
                      {...form.last}
                      disabled={!form.editable || !state.type?.allowPartialDays}
                    >
                      {leavePortions.map((part) => (
                        <option key={part} value={part}>
                          {leave[part]}
                        </option>
                      ))}
                    </AppSelect>
                  )}
                </div>
                <AppTextArea
                  label={text.reason}
                  required
                  maxLength={1000}
                  {...form.reason}
                  readOnly={!form.editable}
                />
              </fieldset>
              <fieldset className="app-editor-section app-report-content">
                <legend>
                  {text.documents}
                  {state.review?.preview.policy.attachmentRequired || state.type?.attachmentRequired
                    ? " *"
                    : ""}
                </legend>
                <p className="app-muted">{text.documentHint}</p>
                {state.evidence.length > 0 ? (
                  <ul className="app-leave-selected-documents">
                    {state.evidence.map((item) => (
                      <li key={item.revisionId}>
                        <span className="app-leave-document-label">
                          <strong>{item.title}</strong>
                          <span className="app-muted">{item.fileName}</span>
                        </span>
                        <AppButton
                          appearance="subtle"
                          icon={<Dismiss20Regular />}
                          aria-label={`${text.remove}: ${item.title}`}
                          disabled={!form.editable}
                          onClick={() => props.onRemoveEvidence(item.revisionId)}
                        />
                      </li>
                    ))}
                  </ul>
                ) : (
                  <p className="app-muted">{text.noDocuments}</p>
                )}
                <div>
                  <AppButton
                    {...restore}
                    appearance="subtle"
                    icon={<Add20Regular />}
                    onClick={form.openEvidence}
                    disabled={!form.canChooseEvidence}
                  >
                    {text.chooseDocuments}
                  </AppButton>
                </div>
              </fieldset>
            </form>
            <aside className="app-leave-creation-review" aria-label={text.reviewTitle}>
              <h2>{text.reviewTitle}</h2>
              {state.stage === "previewing" && <AppLoading label={text.reviewing} />}
              {!view && state.stage !== "previewing" && (
                <p className="app-muted">{text.reviewHint}</p>
              )}
              {view && (
                <>
                  <dl className="app-leave-total">
                    <dt>{text.charged}</dt>
                    <dd>{view.charged}</dd>
                  </dl>
                  <AppResourceTable
                    title={text.balance}
                    minWidth={300}
                    columns={[
                      { id: "year", label: text.year },
                      { id: "before", label: text.before, numeric: true },
                      { id: "requested", label: text.requested, numeric: true },
                      { id: "remaining", label: text.remaining, numeric: true },
                    ]}
                    rows={view.balances}
                  />
                  <div className="app-leave-review-days">
                    <AppResourceTable
                      title={text.days}
                      minWidth={300}
                      columns={[
                        { id: "date", label: leave.date },
                        { id: "portion", label: text.dayLength },
                        { id: "days", label: leave.days, numeric: true },
                      ]}
                      rows={view.days}
                    />
                  </div>
                  {view.excluded && (
                    <div>
                      <h3>{text.excluded}</h3>
                      <p>{view.excluded}</p>
                    </div>
                  )}
                  <p className="app-muted">{view.timezone}</p>
                </>
              )}
            </aside>
          </div>
        )}
      </div>
      {props.picker}
    </section>
  );
}
