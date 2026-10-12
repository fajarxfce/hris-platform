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
import type { useDocumentActionForm } from "../controllers/use-document-action-form";
import { documentActionMessages } from "../i18n/document-action-messages";
import type { DocumentActionState } from "../models/document-action-state";
import type { documentActionCopy, documentActionView } from "../models/document-action-view";

export function DocumentActionPage(props: {
  state: DocumentActionState;
  form: ReturnType<typeof useDocumentActionForm>;
  view: ReturnType<typeof documentActionView> | null;
  copy: ReturnType<typeof documentActionCopy>;
  companyName: string;
  locale: Locale;
  backTo: string;
  onRetry: () => void;
}) {
  const { state, form, view, copy } = props;
  const text = documentActionMessages(props.locale),
    restore = useRestoreFocusTarget();
  return (
    <section aria-busy={state.stage === "loading" || state.stage === "submitting"}>
      <AppPageHeader
        title={copy.title}
        context={props.companyName}
        actions={
          <Link {...restore} to={props.backTo}>
            {text.back}
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
        {view && (
          <>
            <AppPropertyList title={text.review} items={view} />
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
