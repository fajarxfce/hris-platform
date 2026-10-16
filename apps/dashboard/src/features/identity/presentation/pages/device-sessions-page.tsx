import { MessageBar, MessageBarBody } from "@fluentui/react-components";
import { ArrowClockwise20Regular } from "@fluentui/react-icons";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppDialog } from "../../../../core/presentation/components/app-dialog";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import { deviceSessionMessages } from "../i18n/device-session-messages";
import type { deviceSessionProperties, deviceSessionRows } from "../models/device-session-view";
import type { DeviceSessionsState } from "../models/device-sessions-state";

export function DeviceSessionsPage({
  state,
  rows,
  properties,
  locale,
  visible,
  onRefresh,
  onReview,
  onDismiss,
  onEnd,
}: {
  state: DeviceSessionsState;
  rows: ReturnType<typeof deviceSessionRows>;
  properties: ReturnType<typeof deviceSessionProperties>;
  locale: Locale;
  visible: boolean;
  onRefresh: () => void;
  onReview: (id: string) => void;
  onDismiss: () => void;
  onEnd: () => void;
}) {
  const text = deviceSessionMessages(locale);
  return (
    <section
      className="app-report-content"
      aria-busy={state.stage === "loading" || state.stage === "revoking"}
    >
      <AppPageHeader
        title={text.title}
        context={messages(locale).navigationAccount}
        actions={
          <AppButton
            appearance="subtle"
            icon={<ArrowClockwise20Regular />}
            aria-label={messages(locale).refresh}
            disabled={["loading", "confirming", "revoking"].includes(state.stage)}
            onClick={onRefresh}
          />
        }
      />
      <p>{text.description}</p>
      <AppFailure failure={state.failure} locale={locale} />
      {state.stage === "loading" && <AppLoading label={messages(locale).loading} />}
      {state.stage === "revoking" && <AppLoading label={text.pending} />}
      {state.stage === "unconfirmed" && (
        <MessageBar layout="multiline" intent="warning" role="status">
          <MessageBarBody>
            {text.unconfirmed} <AppButton onClick={onEnd}>{text.retry}</AppButton>
          </MessageBarBody>
        </MessageBar>
      )}
      {state.ended && (
        <MessageBar intent="success" role="status">
          <MessageBarBody>{text.ended}</MessageBarBody>
        </MessageBar>
      )}
      {state.sessions &&
        (rows.length === 0 ? (
          <p role="status">{text.empty}</p>
        ) : (
          <AppResourceTable
            title={text.directory}
            rows={rows}
            columns={[
              { id: "device", label: text.device },
              { id: "created", label: text.created },
              { id: "expires", label: text.expires },
            ]}
            action={{ label: text.end, onOpen: onReview, disabled: state.stage !== "ready" }}
          />
        ))}
      <AppDialog
        open={visible && state.stage === "confirming"}
        title={text.confirm}
        busy={false}
        onDismiss={onDismiss}
      >
        <div className="app-form">
          <p>{text.consequence}</p>
          <AppPropertyList title={text.review} items={properties} />
          <div className="app-command-bar">
            <AppButton onClick={onDismiss}>{messages(locale).cancel}</AppButton>
            <AppButton appearance="primary" onClick={onEnd}>
              {text.end}
            </AppButton>
          </div>
        </div>
      </AppDialog>
    </section>
  );
}
