import { MessageBar, MessageBarBody } from "@fluentui/react-components";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppConfirmationDialog } from "../../../../core/presentation/components/app-confirmation-dialog";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppSelect } from "../../../../core/presentation/components/app-select";
import { AppTextArea } from "../../../../core/presentation/components/app-text-area";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { CompanyMembership } from "../../../identity/domain/entities/session";
import { EmployeeAssignmentFields } from "../components/employee-assignment-fields";
import type { useEmploymentTransferForm } from "../controllers/use-employment-transfer-form";
import { employeeCreationMessages } from "../i18n/employee-creation-messages";
import { employmentMessages } from "../i18n/employment-messages";
import { employmentTransferMessages } from "../i18n/employment-transfer-messages";
import { peopleMessages } from "../i18n/people-messages";
import type { EmploymentTransferState } from "../models/employment-transfer-state";

export function EmploymentTransferPage({
  state,
  form,
  companyName,
  targets,
  locale,
  backTo,
  historyTo,
  canChooseOrganization,
  canChooseManager,
  canTransfer,
  onConfirm,
  onDismiss,
  onRetry,
}: {
  state: EmploymentTransferState;
  form: ReturnType<typeof useEmploymentTransferForm>;
  companyName: string;
  targets: readonly CompanyMembership[];
  locale: Locale;
  backTo: string;
  historyTo: string | null;
  canChooseOrganization: boolean;
  canChooseManager: boolean;
  canTransfer: boolean;
  onConfirm: () => void;
  onDismiss: () => void;
  onRetry: () => void;
}) {
  const text = employmentTransferMessages(locale);
  const people = peopleMessages(locale);
  const shared = messages(locale);
  return (
    <section aria-busy={state.stage === "loading" || state.stage === "saving"}>
      <AppPageHeader
        title={text.title}
        context={`${companyName}${state.review ? ` / ${state.review.employee.legalName}` : ""}`}
        actions={<Link to={backTo}>{employmentMessages(locale).back}</Link>}
      />
      <AppFailure failure={state.failure} locale={locale} />
      <div className="app-editor-form">
        <AppSelect
          label={text.target}
          value={state.targetCompanyId}
          disabled={!canTransfer || !form.canSelectTarget || targets.length === 0}
          onChange={(event) => form.selectTarget(event.target.value)}
        >
          <option value="">{text.choose}</option>
          {targets.map((company) => (
            <option key={company.id} value={company.id}>
              {company.code} · {company.name}
            </option>
          ))}
        </AppSelect>
        {targets.length === 0 && <p role="status">{text.noTarget}</p>}
      </div>
      {state.stage === "loading" && <AppLoading label={shared.loading} />}
      {state.stage === "unavailable" && state.targetCompanyId && (
        <AppButton onClick={form.refresh}>{shared.retry}</AppButton>
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
            {text.saved} {historyTo && <Link to={historyTo}>{text.viewHistory}</Link>}
          </MessageBarBody>
        </MessageBar>
      )}
      {state.review && (
        <form className="app-editor-form" onSubmit={form.submit} noValidate>
          <p>
            {people.number}: {state.review.employee.employeeNumber} · {people.version}:{" "}
            {state.review.employee.version}
          </p>
          <div className="app-editor-fields">
            <AppTextField
              label={text.date}
              type="date"
              value={state.review.targetDate}
              readOnly
              hint={text.dateHint}
            />
            <AppTextField
              label={text.number}
              {...form.employeeNumber}
              required
              maxLength={32}
              readOnly={!form.editable}
            />
            <AppSelect label={people.contract} {...form.contract} disabled={!form.editable}>
              <option value="PERMANENT">{people.PERMANENT}</option>
              <option value="FIXED_TERM">{people.FIXED_TERM}</option>
            </AppSelect>
            <AppTextField
              label={people.end}
              type="date"
              {...form.endDate}
              min={state.review.targetDate}
              max="9999-12-31"
              readOnly={!form.editable}
            />
            <AppSelect label={people.status} {...form.status} disabled={!form.editable}>
              <option value="ACTIVE">{people.ACTIVE}</option>
              <option value="PROBATION">{people.PROBATION}</option>
            </AppSelect>
          </div>
          <h2>{text.assignments}</h2>
          <EmployeeAssignmentFields
            assignments={form.assignments}
            editable={form.editable}
            canChooseOrganization={canChooseOrganization}
            canChooseManager={canChooseManager}
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
          <p className="app-muted">{text.note}</p>
          {state.stage === "saving" && <AppLoading label={text.sending} />}
          <div className="app-form-actions">
            <AppConfirmationDialog
              open={state.stage === "confirming" || state.stage === "saving"}
              title={text.confirm}
              message={`${state.review.employee.legalName} → ${state.review.targetCompany.name} · ${state.review.targetDate}. ${text.confirmMessage}`}
              confirmLabel={text.title}
              dismissLabel={shared.cancel}
              busy={state.stage === "saving"}
              onConfirm={onConfirm}
              onDismiss={onDismiss}
              trigger={
                <AppButton type="submit" appearance="primary" disabled={!form.editable}>
                  {text.review}
                </AppButton>
              }
            />
            {(state.stage === "editing" || state.stage === "conflict") && (
              <AppButton onClick={form.refresh}>{text.reload}</AppButton>
            )}
          </div>
        </form>
      )}
    </section>
  );
}
