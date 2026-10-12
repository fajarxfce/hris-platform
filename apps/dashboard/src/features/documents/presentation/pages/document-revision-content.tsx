import { Text } from "@fluentui/react-components";
import { ArrowDownload20Regular, Dismiss20Regular } from "@fluentui/react-icons";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppCommandBar } from "../../../../core/presentation/components/app-command-bar";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { documentActionMessages } from "../i18n/document-action-messages";
import { documentMessages } from "../i18n/document-messages";
import type { DocumentDownloadState } from "../models/document-download-state";
import type { documentRevisionView } from "../models/document-view";

export function DocumentRevisionContent(props: {
  view: ReturnType<typeof documentRevisionView>;
  state: DocumentDownloadState;
  locale: Locale;
  ready: boolean;
  actions: readonly Readonly<{ to: string; label: string }>[];
  onDownload: () => void;
  onCancel: () => void;
}) {
  const text = documentMessages(props.locale);
  return (
    <>
      <AppFailure failure={props.state.failure} locale={props.locale} />
      {props.state.stage === "unavailable" ? (
        <Text role="status">{text.refreshRequired}</Text>
      ) : (
        <>
          {(props.ready || props.actions.length > 0) && (
            <AppCommandBar label={documentActionMessages(props.locale).actions}>
              {props.actions.map((action) => (
                <Link key={action.to} to={action.to}>
                  {action.label}
                </Link>
              ))}
              {props.ready && (
                <AppButton
                  appearance="subtle"
                  icon={<ArrowDownload20Regular />}
                  disabled={props.state.stage === "downloading"}
                  onClick={props.onDownload}
                >
                  {props.state.stage === "downloading" ? text.downloading : text.download}
                </AppButton>
              )}
              {props.state.stage === "downloading" && (
                <AppButton appearance="subtle" icon={<Dismiss20Regular />} onClick={props.onCancel}>
                  {text.cancelDownload}
                </AppButton>
              )}
              {props.state.stage === "started" && <Text role="status">{text.downloadStarted}</Text>}
            </AppCommandBar>
          )}
          <AppPropertyList title={props.view.title} items={props.view.properties} />
          {props.view.attempts.length === 0 ? (
            <Text role="status">{text.noAttempts}</Text>
          ) : (
            <AppResourceTable
              title={text.attempts}
              columns={[
                { id: "number", label: text.attempt, numeric: true },
                { id: "created", label: text.created },
                { id: "reason", label: text.reason },
              ]}
              rows={props.view.attempts}
            />
          )}
        </>
      )}
    </>
  );
}
