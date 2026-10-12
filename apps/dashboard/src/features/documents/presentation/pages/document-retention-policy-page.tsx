import { Text } from "@fluentui/react-components";
import { Link } from "react-router-dom";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { DocumentRetentionPolicyReview } from "../../domain/entities/document-retention-policy";
import { documentRetentionMessages } from "../i18n/document-retention-messages";
import type { DocumentReadState } from "../models/document-read-state";
import type {
  documentRetentionPolicyHistoryRows,
  documentRetentionPolicyProperties,
} from "../models/document-retention-view";
import { DocumentPageFrame } from "../widgets/document-page-frame";
import { DocumentPagination } from "../widgets/document-pagination";

export function DocumentRetentionPolicyPage(props: {
  state: DocumentReadState<DocumentRetentionPolicyReview>;
  title: string;
  properties: ReturnType<typeof documentRetentionPolicyProperties>;
  rows: ReturnType<typeof documentRetentionPolicyHistoryRows>;
  locale: Locale;
  companyName: string;
  backTo: string;
  editTo: string | null;
  firstPage: boolean;
  onFirst: () => void;
  onNext: () => void;
}) {
  const text = documentRetentionMessages(props.locale),
    { state } = props;
  return (
    <DocumentPageFrame
      {...state}
      title={props.title}
      context={`${props.companyName} / ${text.title}`}
      locale={props.locale}
      back={{ to: props.backTo, label: text.back }}
      actions={
        props.editTo && (
          <Link to={props.editTo}>{state.data?.selection.policy ? text.edit : text.configure}</Link>
        )
      }
    >
      {state.data && (
        <>
          <AppPropertyList title={text.policy} items={props.properties} />
          <p>{text.policyHint}</p>
          {props.rows.length === 0 ? (
            <Text role="status">{text.noHistory}</Text>
          ) : (
            <AppResourceTable
              title={text.history}
              columns={[
                { id: "version", label: text.version, numeric: true },
                { id: "retention", label: text.retention },
                { id: "recorded", label: text.recorded },
                { id: "reason", label: text.reason },
                { id: "actor", label: text.actor },
              ]}
              rows={props.rows}
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
  );
}
