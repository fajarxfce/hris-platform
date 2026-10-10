import { MessageBar, MessageBarBody, useRestoreFocusTarget } from "@fluentui/react-components";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { AppTextArea } from "../../../../core/presentation/components/app-text-area";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { useLifecycleCaseCreationForm } from "../controllers/use-lifecycle-case-creation-form";
import { lifecycleCaseCreationMessages } from "../i18n/lifecycle-case-creation-messages";
import { lifecycleMessages } from "../i18n/lifecycle-messages";
import type { LifecycleCaseCreationState } from "../models/lifecycle-case-creation-state";
import type { lifecycleCaseTemplatePreview } from "../models/lifecycle-case-template-preview";

export function LifecycleCaseCreationPage({
  state,
  form,
  preview,
  asOf,
  companyName,
  locale,
  backTo,
  detailTo,
  onRefresh,
  onRetry,
}: {
  state: LifecycleCaseCreationState;
  form: ReturnType<typeof useLifecycleCaseCreationForm>;
  preview: ReturnType<typeof lifecycleCaseTemplatePreview>;
  asOf: string;
  companyName: string;
  locale: Locale;
  backTo: string;
  detailTo: string | null;
  onRefresh: () => void;
  onRetry: () => void;
}) {
  const text = lifecycleCaseCreationMessages(locale);
  const templates = lifecycleMessages(locale);
  const restore = useRestoreFocusTarget();
  return (
    <section aria-busy={state.stage === "loading" || state.stage === "saving"}>
      <AppPageHeader
        title={text.title}
        context={companyName}
        actions={
          <Link {...restore} to={backTo}>
            {text.back}
          </Link>
        }
      />
      <AppFailure failure={state.failure} locale={locale} />
      {state.stage === "loading" && <AppLoading label={messages(locale).loading} />}
      {state.stage === "unavailable" && (
        <AppButton onClick={onRefresh}>{messages(locale).retry}</AppButton>
      )}
      {state.stage === "unconfirmed" && (
        <MessageBar intent="warning" role="status">
          <MessageBarBody>
            {text.unconfirmed}
            <div className="app-reference">
              {templates.operation}: {state.operationId}
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
                {text.view}
              </Link>
            )}
          </MessageBarBody>
        </MessageBar>
      )}
      {state.employee && (
        <form className="app-editor-form" onSubmit={form.submit} noValidate>
          <AppPropertyList
            title={text.employee}
            items={[
              { label: templates.name, value: state.employee.legalName },
              { label: text.employeeNumber, value: state.employee.employeeNumber },
              { label: text.effectiveDate, value: asOf },
            ]}
          />
          <div className="app-editor-fields">
            <div className="app-form">
              <AppTextField
                label={text.template}
                value={form.template ? `${form.template.name} (${form.template.code})` : ""}
                readOnly
              />
              <AppButton {...restore} onClick={form.choose} disabled={!form.editable}>
                {text.choose}
              </AppButton>
            </div>
            <AppTextField
              label={text.targetDate}
              {...form.targetDate}
              type="date"
              min="1900-01-01"
              max="2200-12-31"
              required
              readOnly={!form.editable}
            />
          </div>
          {form.template && (
            <>
              <AppPropertyList
                title={text.template}
                items={[
                  { label: templates.kind, value: templates[form.template.kind] },
                  { label: templates.version, value: form.template.version.toString() },
                ]}
              />
              <AppResourceTable
                title={templates.tasks}
                columns={[
                  { id: "task", label: templates.task },
                  { id: "required", label: templates.required },
                  { id: "due", label: text.dueDate },
                ]}
                rows={preview}
              />
              <p className="app-muted">{text.tasksNote}</p>
            </>
          )}
          <AppTextArea
            label={templates.reason}
            {...form.reason}
            required
            maxLength={1000}
            readOnly={!form.editable}
            resize="vertical"
          />
          {state.stage === "saving" && <AppLoading label={text.saving} />}
          <div className="app-form-actions">
            <AppButton
              type="submit"
              appearance="primary"
              disabled={!form.editable || !form.template}
            >
              {text.create}
            </AppButton>
          </div>
        </form>
      )}
    </section>
  );
}
