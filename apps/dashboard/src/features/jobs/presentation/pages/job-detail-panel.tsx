import { Text } from "@fluentui/react-components";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppCommandBar } from "../../../../core/presentation/components/app-command-bar";
import { AppConfirmationDialog } from "../../../../core/presentation/components/app-confirmation-dialog";
import { AppDetailsPanel } from "../../../../core/presentation/components/app-details-panel";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import { jobMessages } from "../i18n/job-messages";
import type { JobDetailState } from "../models/job-detail-state";
import type { JobDetailView } from "../models/job-view";

export function JobDetailPanel({
  state,
  view,
  locale,
  onClose,
  onRefresh,
  onRequestCancellation,
  onConfirmCancellation,
  onDismissConfirmation,
}: {
  state: JobDetailState;
  view: JobDetailView | null;
  locale: Locale;
  onClose: () => void;
  onRefresh: () => void;
  onRequestCancellation: () => void;
  onConfirmCancellation: () => void;
  onDismissConfirmation: () => void;
}) {
  const text = jobMessages(locale);
  const shared = messages(locale);
  return (
    <AppDetailsPanel open title={text.details} closeLabel={shared.close} onClose={onClose}>
      <AppCommandBar label={text.details}>
        <AppButton
          onClick={onRefresh}
          disabled={state.stage === "loading" || state.stage === "cancelling"}
        >
          {shared.refresh}
        </AppButton>
        <AppConfirmationDialog
          trigger={
            <AppButton
              onClick={onRequestCancellation}
              disabled={state.stage !== "ready" || !view?.canCancel}
            >
              {text.cancel}
            </AppButton>
          }
          open={state.stage === "confirming" || state.stage === "cancelling"}
          title={text.confirmTitle}
          message={text.confirmBody}
          confirmLabel={text.cancel}
          dismissLabel={text.keep}
          busy={state.stage === "cancelling"}
          onConfirm={onConfirmCancellation}
          onDismiss={onDismissConfirmation}
        />
      </AppCommandBar>
      <AppFailure failure={state.failure} locale={locale} />
      {state.stage === "loading" && <AppLoading label={shared.loading} />}
      {state.stage === "unconfirmed" && <Text role="status">{text.unknown}</Text>}
      {view?.cancellationPending && <p role="status">{text.requested}</p>}
      {view && <AppPropertyList title={text.details} items={view.items} />}
    </AppDetailsPanel>
  );
}
