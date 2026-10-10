import {
  MessageBar,
  MessageBarBody,
  Text,
  useRestoreFocusTarget,
} from "@fluentui/react-components";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppTextArea } from "../../../../core/presentation/components/app-text-area";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { useEmployeeImportCreationForm } from "../controllers/use-employee-import-creation-form";
import { employeeCreationMessages } from "../i18n/employee-creation-messages";
import { employeeImportCreationMessages } from "../i18n/employee-import-creation-messages";
import { employeeImportMessages } from "../i18n/employee-import-messages";
import type { EmployeeImportCreationState } from "../models/employee-import-creation-state";

export function EmployeeImportCreationPage({
  state,
  form,
  file,
  locale,
  companyName,
  backTo,
  openTo,
  onSelect,
  onClear,
  onDownload,
  onRetry,
}: {
  state: EmployeeImportCreationState;
  form: ReturnType<typeof useEmployeeImportCreationForm>;
  file: readonly { label: string; value: string }[] | null;
  locale: Locale;
  companyName: string;
  backTo: string;
  openTo: string | null;
  onSelect: () => void;
  onClear: () => void;
  onDownload: () => void;
  onRetry: () => void;
}) {
  const text = employeeImportCreationMessages(locale);
  const shared = employeeImportMessages(locale);
  const restore = useRestoreFocusTarget();
  return (
    <section
      className="app-report-content"
      aria-busy={
        state.stage === "selecting" || state.stage === "downloading" || state.stage === "submitting"
      }
    >
      <AppPageHeader
        title={text.title}
        context={companyName}
        actions={
          <Link {...restore} to={backTo}>
            {shared.back}
          </Link>
        }
      />
      <AppFailure failure={state.failure} locale={locale} />
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
      {state.stage === "saved" && openTo && (
        <MessageBar intent="success" role="status">
          <MessageBarBody>
            {text.saved} <Link to={openTo}>{text.open}</Link>
          </MessageBarBody>
        </MessageBar>
      )}
      {state.stage !== "saved" && state.stage !== "unavailable" && (
        <form className="app-editor-form" onSubmit={form.submit} noValidate>
          <div>
            <p>{text.notice}</p>
            <Text className="app-muted">{text.requirements}</Text>
          </div>
          <div className="app-form-actions">
            <AppButton onClick={onSelect} disabled={!form.editable}>
              {text.choose}
            </AppButton>
            <AppButton onClick={onDownload} disabled={!form.editable}>
              {text.template}
            </AppButton>
          </div>
          {state.stage === "selecting" && <AppLoading label={text.selecting} />}
          {state.stage === "downloading" && <AppLoading label={text.downloading} />}
          {state.templateRequested && <Text role="status">{text.templateRequested}</Text>}
          {file ? (
            <>
              <AppPropertyList title={text.selected} items={file} />
              <AppButton onClick={onClear} disabled={!form.editable}>
                {text.clear}
              </AppButton>
            </>
          ) : (
            <Text>{text.empty}</Text>
          )}
          <AppTextArea
            label={shared.reason}
            {...form.reason}
            maxLength={1000}
            required
            resize="vertical"
            readOnly={!form.editable}
          />
          {state.stage === "submitting" && <AppLoading label={text.preparing} />}
          <div className="app-form-actions">
            <AppButton appearance="primary" type="submit" disabled={!form.editable || !file}>
              {text.prepare}
            </AppButton>
          </div>
        </form>
      )}
    </section>
  );
}
