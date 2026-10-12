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
import type { WorkPeriodIntent } from "../../domain/entities/work-period-action-context";
import type { useWorkPeriodActionForm } from "../controllers/use-work-period-action-form";
import { workPeriodMessages } from "../i18n/work-period-messages";
import type { WorkPeriodActionState } from "../models/work-period-action-state";
import type { workPeriodOverviewView } from "../models/work-period-view";

export function WorkPeriodActionPage(props: {
  state: WorkPeriodActionState;
  form: ReturnType<typeof useWorkPeriodActionForm>;
  view: ReturnType<typeof workPeriodOverviewView> | null;
  intent: WorkPeriodIntent;
  companyName: string;
  locale: Locale;
  backTo: string;
  onRetry: () => void;
}) {
  const { state, form, view, intent, locale } = props,
    text = workPeriodMessages(locale);
  const restore = useRestoreFocusTarget();
  return (
    <section
      className="app-report-content app-record-editor app-work-period-action"
      aria-busy={state.stage === "loading" || state.stage === "submitting"}
    >
      <AppPageHeader
        title={text[intent]}
        context={props.companyName}
        actions={
          <Link {...restore} to={props.backTo}>
            {text.back}
          </Link>
        }
      />
      <AppFailure failure={state.failure} locale={locale} />
      {state.stage === "loading" && <AppLoading label={messages(locale).loading} />}
      {state.stage === "submitting" && <AppLoading label={text.saving} />}
      {state.stage === "unavailable" && (
        <div>
          <AppButton icon={<ArrowClockwise20Regular />} onClick={form.refresh}>
            {messages(locale).retry}
          </AppButton>
        </div>
      )}
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
            {intent === "close" ? text.closeSaved : text.recoverSaved}{" "}
            <Link {...restore} to={props.backTo}>
              {text.viewPeriod}
            </Link>
          </MessageBarBody>
        </MessageBar>
      )}
      {view && (
        <>
          <AppPropertyList
            title={text.overview}
            items={[
              { label: text.month, value: view.month },
              { label: text.status, value: view.status },
              ...view.properties,
            ]}
          />
          <form
            className="app-editor-form"
            aria-label={text[intent]}
            onSubmit={form.submit}
            noValidate
          >
            <p>{intent === "close" ? text.closeHint : text.recoverHint}</p>
            <AppTextField
              label={text.reason}
              {...form.reason}
              required
              maxLength={1000}
              readOnly={!form.editable}
              error={form.invalidReason ? text.reasonRequired : undefined}
            />
            <div className="app-form-actions">
              <AppButton appearance="primary" type="submit" disabled={!form.editable}>
                {text[intent]}
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
    </section>
  );
}
