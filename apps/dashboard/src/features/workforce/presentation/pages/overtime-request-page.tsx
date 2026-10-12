import { Text } from "@fluentui/react-components";
import { ArrowClockwise20Regular } from "@fluentui/react-icons";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppCommandBar } from "../../../../core/presentation/components/app-command-bar";
import { AppDetailsPanel } from "../../../../core/presentation/components/app-details-panel";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { useOvertimeRequest } from "../controllers/use-overtime-request";
import { overtimeActionMessages } from "../i18n/overtime-action-messages";
import { overtimeMessages } from "../i18n/overtime-messages";
import type { overtimeActionLinks } from "../models/overtime-action-view";
import type { overtimeDetailsView } from "../models/overtime-view";

export function OvertimeRequestPage(props: {
  state: ReturnType<typeof useOvertimeRequest>;
  view: ReturnType<typeof overtimeDetailsView> | null;
  companyName: string;
  locale: Locale;
  actions: ReturnType<typeof overtimeActionLinks>;
  backTo: string;
  backLabel: string;
  approvalTo: string | null;
  firstHistory: boolean;
  onLatest: () => void;
  onOlder: () => void;
  onOpenChange: (revision: string) => void;
  onCloseChange: () => void;
}) {
  const { state, view, locale } = props,
    text = overtimeMessages(locale);
  return (
    <>
      <section aria-busy={state.loading}>
        <AppPageHeader
          title={text.details}
          context={props.companyName}
          actions={
            <>
              <Link to={props.backTo}>{props.backLabel}</Link>
              <AppButton
                appearance="subtle"
                icon={<ArrowClockwise20Regular />}
                onClick={state.refresh}
                disabled={state.loading}
              >
                {messages(locale).refresh}
              </AppButton>
            </>
          }
        />
        <AppFailure failure={state.failure} locale={locale} />
        {props.actions.length > 0 && (
          <AppCommandBar label={overtimeActionMessages(locale).actions}>
            {props.actions.map((action) => (
              <Link key={action.intent} to={action.to}>
                {action.label}
              </Link>
            ))}
          </AppCommandBar>
        )}
        {state.loading && <AppLoading label={messages(locale).loading} />}
        {view && (
          <div className="app-report-content">
            <AppPropertyList title={text.overview} items={view.properties} />
            <AppResourceTable
              title={text.intervals}
              columns={[
                { id: "record", label: text.period },
                { id: "start", label: text.start },
                { id: "end", label: text.end },
                { id: "break", label: text.break, numeric: true },
                { id: "duration", label: text.duration, numeric: true },
              ]}
              rows={view.intervals}
            />
            <AppPropertyList title={text.schedule} items={view.schedule} />
            {view.approval && (
              <section aria-label={text.approval}>
                <AppPropertyList title={text.approval} items={view.approval} />
                {props.approvalTo && <Link to={props.approvalTo}>{text.openApproval}</Link>}
              </section>
            )}
            {view.history.length === 0 ? (
              <Text role="status">{text.emptyHistory}</Text>
            ) : (
              <AppResourceTable
                title={text.history}
                columns={[
                  { id: "revision", label: text.revision, numeric: true },
                  { id: "kind", label: text.kind },
                  { id: "status", label: text.status },
                  { id: "recorded", label: text.recorded },
                ]}
                rows={view.history}
                action={{ label: text.viewChange, onOpen: props.onOpenChange }}
              />
            )}
            <fieldset className="app-pagination" aria-label={text.historyPages}>
              <AppButton
                appearance="subtle"
                disabled={props.firstHistory || state.loading}
                onClick={props.onLatest}
              >
                {text.latest}
              </AppButton>
              <AppButton
                appearance="subtle"
                disabled={state.details?.history.nextCursor == null || state.loading}
                onClick={props.onOlder}
              >
                {text.older}
              </AppButton>
            </fieldset>
          </div>
        )}
      </section>
      <AppDetailsPanel
        open={view?.change != null}
        title={text.changeDetails}
        closeLabel={messages(locale).close}
        onClose={props.onCloseChange}
      >
        {view?.change && <AppPropertyList title={text.history} items={view.change} />}
      </AppDetailsPanel>
    </>
  );
}
