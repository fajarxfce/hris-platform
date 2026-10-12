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
import type { useDocumentRetirementForm } from "../controllers/use-document-retirement-form";
import { documentRetentionLifecycleMessages } from "../i18n/document-retention-lifecycle-messages";
import { documentRetirementMessages } from "../i18n/document-retirement-messages";
import type { DocumentRetirementState } from "../models/document-retirement-state";
import type { documentRetirementView } from "../models/document-retirement-view";

export function DocumentRetirementPage(props: {
  state: DocumentRetirementState;
  form: ReturnType<typeof useDocumentRetirementForm>;
  view: ReturnType<typeof documentRetirementView> | null;
  locale: Locale;
  companyName: string;
  backTo: string;
  onRetry: () => void;
}) {
  const text = documentRetirementMessages(props.locale),
    lifecycle = documentRetentionLifecycleMessages(props.locale),
    restore = useRestoreFocusTarget(),
    { state, form, view } = props;
  return (
    <section
      className="app-record-editor app-document-retirement"
      aria-busy={state.stage === "loading" || state.stage === "submitting"}
    >
      <AppPageHeader
        title={text.title}
        context={props.companyName}
        actions={
          <Link {...restore} to={props.backTo}>
            {lifecycle.backDocument}
          </Link>
        }
      />
      <div className="app-report-content">
        <AppFailure failure={state.failure} locale={props.locale} />
        {state.stage === "loading" && <AppLoading label={messages(props.locale).loading} />}
        {state.stage === "unavailable" && (
          <AppButton onClick={form.refresh}>{messages(props.locale).retry}</AppButton>
        )}
        {state.stage === "submitting" && <AppLoading label={lifecycle.saving} />}
        {state.stage === "unconfirmed" && (
          <MessageBar intent="warning" layout="multiline" role="status">
            <MessageBarBody>
              {lifecycle.unconfirmed}
              <div className="app-reference">
                {lifecycle.operation}: {state.operationId}
              </div>
              <AppButton onClick={props.onRetry}>{lifecycle.retry}</AppButton>
            </MessageBarBody>
          </MessageBar>
        )}
        {state.stage === "saved" && (
          <MessageBar intent="success" layout="multiline" role="status">
            <MessageBarBody>
              {text.saved}{" "}
              <Link {...restore} to={props.backTo}>
                {lifecycle.view}
              </Link>
            </MessageBarBody>
          </MessageBar>
        )}
        {view && (
          <>
            <AppPropertyList title={text.overview} items={view.properties} />
            <AppPropertyList title={lifecycle.overview} items={view.retention} />
            {state.stage === "blocked" ? (
              <>
                <MessageBar intent="info" layout="multiline" role="status">
                  <MessageBarBody>
                    <strong>{text.blocked}</strong>
                    <p>{view.blocker}</p>
                  </MessageBarBody>
                </MessageBar>
                <AppButton
                  {...restore}
                  appearance="subtle"
                  icon={<ArrowClockwise20Regular />}
                  onClick={form.refresh}
                >
                  {lifecycle.reload}
                </AppButton>
              </>
            ) : (
              <form className="app-editor-form" onSubmit={form.submit} noValidate>
                <h2>{text.confirm}</h2>
                <p>{text.hint}</p>
                {state.review?.published && <p>{text.publicationHint}</p>}
                <AppTextField
                  label={lifecycle.reason}
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
                      {lifecycle.reload}
                    </AppButton>
                  )}
                </div>
              </form>
            )}
          </>
        )}
      </div>
    </section>
  );
}
