import { MessageBar, MessageBarBody, useRestoreFocusTarget } from "@fluentui/react-components";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppSelect } from "../../../../core/presentation/components/app-select";
import { AppTextArea } from "../../../../core/presentation/components/app-text-area";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import { EmployeeAssignmentFields } from "../components/employee-assignment-fields";
import type { useEmploymentEditorForm } from "../controllers/use-employment-editor-form";
import { employeeCreationMessages } from "../i18n/employee-creation-messages";
import { employmentMessages } from "../i18n/employment-messages";
import { peopleMessages } from "../i18n/people-messages";
import type { EmploymentEditorState } from "../models/employment-editor-state";

export function EmploymentEditorPage({
  state,
  form,
  companyName,
  locale,
  backTo,
  detailTo,
  canChooseOrganization,
  onRetry,
}: {
  state: EmploymentEditorState;
  form: ReturnType<typeof useEmploymentEditorForm>;
  companyName: string;
  locale: Locale;
  backTo: string;
  detailTo: string | null;
  canChooseOrganization: boolean;
  onRetry: () => void;
}) {
  const text = employmentMessages(locale);
  const people = peopleMessages(locale);
  const restore = useRestoreFocusTarget();
  return (
    <section aria-busy={state.stage === "loading" || state.stage === "saving"}>
      <AppPageHeader
        title={text.edit}
        context={`${companyName}${state.details ? ` / ${state.details.employee.legalName}` : ""}`}
        actions={
          <Link {...restore} to={backTo}>
            {text.back}
          </Link>
        }
      />
      <AppFailure failure={state.failure} locale={locale} />
      {state.stage === "loading" && <AppLoading label={messages(locale).loading} />}
      {state.stage === "unavailable" && (
        <AppButton onClick={form.refresh}>{messages(locale).retry}</AppButton>
      )}
      {state.stage === "unconfirmed" && (
        <MessageBar intent="warning" role="status">
          <MessageBarBody>
            {text.unconfirmed}
            <div className="app-reference">
              {employeeCreationMessages(locale).operation}: {state.operationId}
            </div>
            <AppButton onClick={onRetry}>{text.retry}</AppButton>
          </MessageBarBody>
        </MessageBar>
      )}
      {state.stage === "saved" && (
        <MessageBar intent="success" role="status">
          <MessageBarBody>
            {text.saved}{" "}
            {detailTo && (
              <Link {...restore} to={detailTo}>
                {people.view}
              </Link>
            )}
          </MessageBarBody>
        </MessageBar>
      )}
      {state.details && (
        <form className="app-editor-form" onSubmit={form.submit} noValidate>
          <p>
            {text.loadedDate}: {state.details.asOf} · {people.number}:{" "}
            {state.details.employee.employeeNumber} · {people.version}:{" "}
            {state.details.employee.version}
          </p>
          <div className="app-editor-fields">
            <AppTextField
              label={people.start}
              type="date"
              value={state.details.employee.terms.startDate}
              readOnly
              hint={text.startHint}
            />
            <AppTextField
              label={people.effectiveFrom}
              {...form.effectiveFrom}
              type="date"
              min="0001-01-01"
              max="9999-12-31"
              required
              readOnly={!form.editable}
              hint={text.effectiveHint}
            />
            <AppSelect label={people.contract} {...form.contract} disabled={!form.editable}>
              <option value="PERMANENT">{people.PERMANENT}</option>
              <option value="FIXED_TERM">{people.FIXED_TERM}</option>
            </AppSelect>
            <AppTextField
              label={people.end}
              {...form.endDate}
              type="date"
              min="0001-01-01"
              max="9999-12-31"
              readOnly={!form.editable}
            />
            <AppSelect label={people.status} {...form.status} disabled={!form.editable}>
              <option value="ACTIVE">{people.ACTIVE}</option>
              <option value="PROBATION">{people.PROBATION}</option>
              <option value="SUSPENDED">{people.SUSPENDED}</option>
              <option value="ENDED">{people.ENDED}</option>
            </AppSelect>
          </div>
          <h2>{employeeCreationMessages(locale).assignments}</h2>
          <EmployeeAssignmentFields
            assignments={form.assignments}
            editable={form.editable}
            canChooseOrganization={canChooseOrganization}
            canChooseManager
            locale={locale}
            onChoose={form.choose}
            onClear={form.clear}
          />
          <AppTextArea
            label={people.reason}
            {...form.reason}
            required
            maxLength={1000}
            resize="vertical"
            readOnly={!form.editable}
          />
          {state.stage === "saving" && <AppLoading label={text.saving} />}
          <div className="app-form-actions">
            <AppButton type="submit" appearance="primary" disabled={!form.editable}>
              {text.save}
            </AppButton>
            {(state.stage === "editing" || state.stage === "conflict") && (
              <AppButton {...restore} onClick={form.refresh}>
                {text.reload}
              </AppButton>
            )}
          </div>
        </form>
      )}
    </section>
  );
}
