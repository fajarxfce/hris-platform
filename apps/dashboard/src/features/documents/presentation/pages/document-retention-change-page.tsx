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
import type { DocumentRetentionIntent } from "../../domain/entities/document-retention";
import type { useDocumentRetentionChangeForm } from "../controllers/use-document-retention-change-form";
import { documentRetentionLifecycleMessages } from "../i18n/document-retention-lifecycle-messages";
import type { DocumentRetentionChangeState } from "../models/document-retention-change-state";
import type { documentRetentionProperties } from "../models/document-retention-lifecycle-view";

export function DocumentRetentionChangePage(props: {
  state: DocumentRetentionChangeState;
  form: ReturnType<typeof useDocumentRetentionChangeForm>;
  properties: ReturnType<typeof documentRetentionProperties>;
  intent: DocumentRetentionIntent;
  locale: Locale;
  companyName: string;
  backTo: string;
  onRetry: () => void;
}) {
  const text = documentRetentionLifecycleMessages(props.locale),
    copy = text.actions[props.intent],
    restore = useRestoreFocusTarget(),
    { state, form } = props;
  return (
    <section
      className="app-record-editor"
      aria-busy={state.stage === "loading" || state.stage === "submitting"}
    >
      <AppPageHeader
        title={copy.title}
        context={props.companyName}
        actions={
          <Link {...restore} to={props.backTo}>
            {text.backDocument}
          </Link>
        }
      />
      <div className="app-report-content">
        <AppFailure failure={state.failure} locale={props.locale} />
        {state.stage === "loading" && <AppLoading label={messages(props.locale).loading} />}
        {state.stage === "unavailable" && (
          <AppButton onClick={form.refresh}>{messages(props.locale).retry}</AppButton>
        )}
        {state.stage === "submitting" && <AppLoading label={text.saving} />}
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
              {copy.saved}{" "}
              <Link {...restore} to={props.backTo}>
                {text.view}
              </Link>
            </MessageBarBody>
          </MessageBar>
        )}
        {state.review && (
          <>
            <AppPropertyList title={text.overview} items={props.properties} />
            <form className="app-editor-form" onSubmit={form.submit} noValidate>
              <p>{copy.hint}</p>
              <AppTextField
                label={text.reason}
                {...form.reason}
                maxLength={1000}
                required
                readOnly={!form.editable}
              />
              <div className="app-form-actions">
                <AppButton appearance="primary" type="submit" disabled={!form.editable}>
                  {text.confirm}
                </AppButton>
                {(state.stage === "reviewing" || state.stage === "conflict") && (
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
