import { MessageBar, MessageBarBody, useRestoreFocusTarget } from "@fluentui/react-components";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppDetailsPanel } from "../../../../core/presentation/components/app-details-panel";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppTextArea } from "../../../../core/presentation/components/app-text-area";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { LifecycleCaseAction } from "../../domain/entities/lifecycle-case-change";
import type { useLifecycleCaseActionForm } from "../controllers/use-lifecycle-case-action-form";
import { lifecycleCaseActionMessages } from "../i18n/lifecycle-case-action-messages";
import { lifecycleCaseMessages } from "../i18n/lifecycle-case-messages";
import { lifecycleMessages } from "../i18n/lifecycle-messages";
import type { LifecycleCaseActionState } from "../models/lifecycle-case-action-state";

export function LifecycleCaseActionPage({
  action,
  properties,
  state,
  form,
  locale,
  onRetry,
}: {
  action: LifecycleCaseAction;
  properties: readonly { label: string; value: string }[];
  state: LifecycleCaseActionState;
  form: ReturnType<typeof useLifecycleCaseActionForm>;
  locale: Locale;
  onRetry: () => void;
}) {
  const text = lifecycleCaseActionMessages(locale);
  const shared = lifecycleCaseMessages(locale);
  const restore = useRestoreFocusTarget();
  return (
    <AppDetailsPanel open title={text[action]} closeLabel={shared.close} onClose={form.close}>
      <div className="app-report-content" aria-busy={state.stage === "saving"}>
        <AppPropertyList title={shared.details} items={properties} />
        <p>{action === "cancel" ? text.cancelHint : text.completeOnboardingHint}</p>
        <AppFailure failure={state.failure} locale={locale} />
        {state.stage === "unconfirmed" && (
          <MessageBar intent="warning" role="status">
            <MessageBarBody>
              {text.unconfirmed}
              <div className="app-reference">
                {lifecycleMessages(locale).operation}: {state.operationId}
              </div>
              <AppButton onClick={onRetry}>{text.retry}</AppButton>
            </MessageBarBody>
          </MessageBar>
        )}
        <form className="app-form" onSubmit={form.submit} noValidate>
          <AppTextArea
            label={shared.reason}
            {...form.reason}
            required
            maxLength={1000}
            readOnly={!form.editable}
            resize="vertical"
          />
          {state.stage === "saving" && <AppLoading label={text.saving} />}
          <div className="app-form-actions">
            <AppButton type="submit" appearance="primary" disabled={!form.editable}>
              {text[action]}
            </AppButton>
            <AppButton {...restore} onClick={form.reload} disabled={state.stage === "saving"}>
              {text.reload}
            </AppButton>
          </div>
        </form>
      </div>
    </AppDetailsPanel>
  );
}
