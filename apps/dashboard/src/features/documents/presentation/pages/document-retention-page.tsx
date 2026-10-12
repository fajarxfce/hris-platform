import { Text } from "@fluentui/react-components";
import { Link } from "react-router-dom";
import { AppDetailsPanel } from "../../../../core/presentation/components/app-details-panel";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { DocumentRetentionReview } from "../../domain/entities/document-retention";
import { documentRetentionLifecycleMessages } from "../i18n/document-retention-lifecycle-messages";
import type { DocumentReadState } from "../models/document-read-state";
import type {
  documentRetentionChangeProperties,
  documentRetentionHistoryRows,
  documentRetentionProperties,
} from "../models/document-retention-lifecycle-view";
import { DocumentPageFrame } from "../widgets/document-page-frame";
import { DocumentPagination } from "../widgets/document-pagination";

export function DocumentRetentionPage(props: {
  state: DocumentReadState<DocumentRetentionReview>;
  properties: ReturnType<typeof documentRetentionProperties>;
  rows: ReturnType<typeof documentRetentionHistoryRows>;
  change: ReturnType<typeof documentRetentionChangeProperties> | null;
  actions: readonly { to: string; label: string }[];
  locale: Locale;
  companyName: string;
  backTo: string;
  documentTo: string | null;
  firstPage: boolean;
  onFirst: () => void;
  onNext: () => void;
  onOpenChange: (version: string) => void;
  onCloseChange: () => void;
}) {
  const text = documentRetentionLifecycleMessages(props.locale),
    { state } = props;
  return (
    <>
      <DocumentPageFrame
        {...state}
        title={text.title}
        context={props.companyName}
        locale={props.locale}
        back={{ to: props.backTo, label: text.back }}
        actions={
          <>
            {props.documentTo && <Link to={props.documentTo}>{text.details}</Link>}
            {props.actions.map((action) => (
              <Link key={action.to} to={action.to}>
                {action.label}
              </Link>
            ))}
          </>
        }
      >
        {state.data && (
          <>
            <AppPropertyList title={text.overview} items={props.properties} />
            {props.rows.length === 0 ? (
              <Text role="status">{text.noHistory}</Text>
            ) : (
              <AppResourceTable
                title={text.history}
                columns={[
                  { id: "version", label: text.version, numeric: true },
                  { id: "action", label: text.action },
                  { id: "recorded", label: text.recorded },
                  { id: "reason", label: text.reason },
                ]}
                rows={props.rows}
                action={{ label: text.viewChange, onOpen: props.onOpenChange }}
              />
            )}
          </>
        )}
        <DocumentPagination
          {...props}
          loading={state.loading}
          hasNext={state.data?.history.nextCursor != null}
        />
      </DocumentPageFrame>
      <AppDetailsPanel
        open={props.change !== null}
        title={text.change}
        closeLabel={text.close}
        onClose={props.onCloseChange}
      >
        {props.change && <AppPropertyList title={text.overview} items={props.change} />}
      </AppDetailsPanel>
    </>
  );
}
