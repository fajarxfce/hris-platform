import { MessageBar, MessageBarBody } from "@fluentui/react-components";
import { ArrowClockwise20Regular } from "@fluentui/react-icons";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppTextArea } from "../../../../core/presentation/components/app-text-area";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { LeaveBatchDetails } from "../../domain/entities/leave-batch-details";
import type { useLeaveBatchReasonForm } from "../controllers/use-leave-batch-reason-form";
import { leaveBatchMessages } from "../i18n/leave-batch-messages";
import type { LeaveBatchCommandState } from "../models/leave-batch-state";
import type { leaveBatchView } from "../models/leave-batch-view";
import { LeaveBatchCommandFeedback } from "../widgets/leave-batch-command-feedback";
export function LeaveBatchResumePage(props: {
  state: LeaveBatchCommandState<LeaveBatchDetails>;
  form: ReturnType<typeof useLeaveBatchReasonForm>;
  view: ReturnType<typeof leaveBatchView> | null;
  canSubmit: boolean;
  locale: Locale;
  companyName: string;
  backTo: string;
  successTo: string | null;
  onRetry: () => void;
}) {
  const { state, form, locale, view } = props,
    text = leaveBatchMessages(locale);
  return (
    <section
      className="app-report-content app-record-editor"
      aria-busy={state.stage === "loading" || state.stage === "saving"}
    >
      <AppPageHeader
        title={text.resume}
        context={props.companyName}
        actions={<Link to={props.backTo}>{text.backToDetail}</Link>}
      />
      <AppFailure failure={state.failure} locale={locale} />
      {state.stage === "loading" && <AppLoading label={messages(locale).loading} />}
      <LeaveBatchCommandFeedback
        stage={state.stage}
        operationId={state.operationId}
        locale={locale}
        successTo={props.successTo}
        successMessage={text.resumed}
        onRetry={props.onRetry}
      />
      {["reviewing", "conflict", "unavailable"].includes(state.stage) && (
        <div>
          <AppButton appearance="subtle" icon={<ArrowClockwise20Regular />} onClick={form.reload}>
            {text.reload}
          </AppButton>
        </div>
      )}
      {view && <AppPropertyList title={text.overview} items={view.properties} />}
      {state.stage === "reviewing" && !props.canSubmit && (
        <MessageBar intent="warning" role="status">
          <MessageBarBody>{text.unavailableAction}</MessageBarBody>
        </MessageBar>
      )}
      {state.stage === "reviewing" && props.canSubmit && (
        <form
          className="app-editor-form"
          aria-label={text.resume}
          onSubmit={form.submit}
          noValidate
        >
          <p>{text.resumeNote}</p>
          <AppTextArea label={text.reason} rows={3} maxLength={1000} required {...form.reason} />
          <div className="app-form-actions">
            <AppButton appearance="primary" type="submit">
              {text.resume}
            </AppButton>
          </div>
        </form>
      )}
    </section>
  );
}
