import { MessageBar, MessageBarBody, useRestoreFocusTarget } from "@fluentui/react-components";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppSelect } from "../../../../core/presentation/components/app-select";
import { AppTextArea } from "../../../../core/presentation/components/app-text-area";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { useEmployeeCreationForm } from "../controllers/use-employee-creation-form";
import { employeeCreationMessages } from "../i18n/employee-creation-messages";
import { peopleMessages } from "../i18n/people-messages";
import { personProfileMessages, profileFieldError } from "../i18n/person-profile-messages";
import type { EmployeeCreationState } from "../models/employee-creation-state";

export function EmployeeCreationPage({
  state,
  form,
  companyName,
  locale,
  backTo,
  detailTo,
  canChooseOrganization,
  canChooseManager,
  onRetry,
}: {
  state: EmployeeCreationState;
  form: ReturnType<typeof useEmployeeCreationForm>;
  companyName: string;
  locale: Locale;
  backTo: string;
  detailTo: string | null;
  canChooseOrganization: boolean;
  canChooseManager: boolean;
  onRetry: () => void;
}) {
  const text = employeeCreationMessages(locale);
  const people = peopleMessages(locale);
  const profile = personProfileMessages(locale);
  const restore = useRestoreFocusTarget();
  return (
    <section aria-busy={state.stage === "saving"}>
      <AppPageHeader
        title={text.title}
        context={companyName}
        actions={
          <Link {...restore} to={backTo}>
            {profile.back}
          </Link>
        }
      />
      <AppFailure failure={state.failure} locale={locale} />
      {state.stage === "unconfirmed" && (
        <MessageBar intent="warning" role="status">
          <MessageBarBody>
            {text.unconfirmed}
            <div className="app-reference">
              {text.operation}: {state.operationId}
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
      {state.stage !== "unavailable" && (
        <form className="app-editor-form" onSubmit={form.submit} noValidate>
          <h2>{text.personal}</h2>
          <div className="app-editor-fields">
            <AppTextField
              label={profile.legalName}
              {...form.legalName}
              required
              maxLength={200}
              readOnly={!form.editable}
              error={profileFieldError(state.failure?.fields.legalName, locale)}
            />
            <AppTextField
              label={profile.birthDate}
              {...form.birthDate}
              type="date"
              min="0001-01-01"
              max="9999-12-31"
              readOnly={!form.editable}
              error={profileFieldError(state.failure?.fields.birthDate, locale)}
            />
            <AppTextField
              label={profile.nationality}
              {...form.nationality}
              required
              maxLength={2}
              hint={profile.countryHint}
              readOnly={!form.editable}
              error={profileFieldError(state.failure?.fields.nationality, locale)}
            />
            <AppTextField
              label={profile.email}
              {...form.email}
              type="email"
              maxLength={254}
              readOnly={!form.editable}
              error={profileFieldError(state.failure?.fields.email, locale)}
            />
          </div>
          <h2>{text.employment}</h2>
          <div className="app-editor-fields">
            <AppTextField
              label={people.number}
              {...form.employeeNumber}
              required
              maxLength={32}
              hint={text.numberHint}
              readOnly={!form.editable}
            />
            <AppSelect label={people.contract} {...form.contract} disabled={!form.editable}>
              <option value="PERMANENT">{people.PERMANENT}</option>
              <option value="FIXED_TERM">{people.FIXED_TERM}</option>
            </AppSelect>
            <AppTextField
              label={people.start}
              {...form.startDate}
              type="date"
              required
              min="0001-01-01"
              max="9999-12-31"
              hint={text.effectiveHint}
              readOnly={!form.editable}
            />
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
          <h2>{text.assignments}</h2>
          <div className="app-editor-fields">
            {form.assignments.map((assignment) => (
              <section
                key={assignment.kind}
                className="app-assignment"
                aria-label={text[assignment.kind]}
              >
                <strong>{text[assignment.kind]}</strong>
                <span>{assignment.value?.label ?? text.unassigned}</span>
                <div className="app-form-actions">
                  <AppButton
                    {...restore}
                    aria-label={`${text.choose} ${text[assignment.kind]}`}
                    disabled={
                      !form.editable ||
                      !(assignment.kind === "MANAGER" ? canChooseManager : canChooseOrganization)
                    }
                    onClick={() => form.choose(assignment.kind)}
                  >
                    {text.choose}
                  </AppButton>
                  {assignment.value && (
                    <AppButton
                      {...restore}
                      aria-label={`${text.clear} ${text[assignment.kind]}`}
                      disabled={!form.editable}
                      onClick={() => form.clear(assignment.kind)}
                    >
                      {text.clear}
                    </AppButton>
                  )}
                </div>
                {!(assignment.kind === "MANAGER" ? canChooseManager : canChooseOrganization) && (
                  <small>{text.accessHint}</small>
                )}
              </section>
            ))}
          </div>
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
              {text.create}
            </AppButton>
          </div>
        </form>
      )}
    </section>
  );
}
