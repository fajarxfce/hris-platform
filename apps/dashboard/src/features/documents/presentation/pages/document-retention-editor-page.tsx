import { MessageBar, MessageBarBody, useRestoreFocusTarget } from "@fluentui/react-components";
import { ArrowClockwise20Regular } from "@fluentui/react-icons";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { useDocumentRetentionForm } from "../controllers/use-document-retention-form";
import { documentRetentionMessages } from "../i18n/document-retention-messages";
import type { DocumentRetentionEditorState } from "../models/document-retention-editor-state";
import type { documentRetentionPolicyProperties } from "../models/document-retention-view";

export function DocumentRetentionEditorPage(props: {
  state: DocumentRetentionEditorState;
  form: ReturnType<typeof useDocumentRetentionForm>;
  properties: ReturnType<typeof documentRetentionPolicyProperties>;
  locale: Locale;
  companyName: string;
  backTo: string;
  onRetry: () => void;
}) {
  const text = documentRetentionMessages(props.locale),
    restore = useRestoreFocusTarget(),
    { state, form } = props;
  return (
    <section
      className="app-record-editor"
      aria-busy={state.stage === "loading" || state.stage === "saving"}
    >
      <AppPageHeader
        title={text.editor}
        context={props.companyName}
        actions={
          <Link {...restore} to={props.backTo}>
            {text.backPolicy}
          </Link>
        }
      />
      <div className="app-report-content">
        <AppFailure failure={state.failure} locale={props.locale} />
        {state.stage === "loading" && <AppLoading label={messages(props.locale).loading} />}
        {state.stage === "unavailable" && (
          <AppButton onClick={form.refresh}>{messages(props.locale).retry}</AppButton>
        )}
        {state.stage === "saving" && <AppLoading label={text.saving} />}
        {state.stage === "unconfirmed" && (
          <MessageBar intent="warning" layout="multiline" role="status">
            <MessageBarBody>
              {text.unconfirmed}
              <div className="app-reference">
                {text.operation}: {state.operationId}
              </div>
              <AppButton onClick={props.onRetry}>{text.retry}</AppButton>
            </MessageBarBody>
          </MessageBar>
        )}
        {state.stage === "saved" && (
          <MessageBar intent="success" layout="multiline" role="status">
            <MessageBarBody>
              {text.saved}{" "}
              <Link {...restore} to={props.backTo}>
                {text.view}
              </Link>
            </MessageBarBody>
          </MessageBar>
        )}
        {state.selection && (
          <>
            <AppPropertyList title={text.policy} items={props.properties} />
            <form className="app-editor-form" onSubmit={form.submit} noValidate>
              <p>{text.policyHint}</p>
              <AppTextField
                label={text.daysLabel}
                hint={text.daysHint}
                {...form.retentionDays}
                inputMode="numeric"
                maxLength={5}
                readOnly={!form.editable}
              />
              <AppTextField
                label={text.reason}
                {...form.reason}
                maxLength={1000}
                required
                readOnly={!form.editable}
              />
              <div className="app-form-actions">
                <AppButton appearance="primary" type="submit" disabled={!form.editable}>
                  {text.save}
                </AppButton>
                {(state.stage === "editing" || state.stage === "conflict") && (
                  <AppButton
                    {...restore}
                    appearance="subtle"
                    icon={<ArrowClockwise20Regular />}
                    onClick={form.refresh}
                  >
                    {text.reload}
                  </AppButton>
                )}
              </div>
            </form>
          </>
        )}
      </div>
    </section>
  );
}
