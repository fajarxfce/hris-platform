import { Text } from "@fluentui/react-components";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { DocumentMetadataReview } from "../../domain/entities/document-metadata";
import { documentMetadataMessages } from "../i18n/document-metadata-messages";
import type {
  documentMetadataProperties,
  documentMetadataRows,
} from "../models/document-metadata-view";
import type { DocumentReadState } from "../models/document-read-state";
import { DocumentPageFrame } from "../widgets/document-page-frame";
import { DocumentPagination } from "../widgets/document-pagination";

export function DocumentMetadataHistoryPage(props: {
  state: DocumentReadState<DocumentMetadataReview>;
  properties: ReturnType<typeof documentMetadataProperties>;
  rows: ReturnType<typeof documentMetadataRows>;
  locale: Locale;
  companyName: string;
  backTo: string;
  firstPage: boolean;
  onFirst: () => void;
  onNext: () => void;
}) {
  const text = documentMetadataMessages(props.locale),
    { state } = props;
  return (
    <DocumentPageFrame
      {...state}
      title={text.history}
      context={props.companyName}
      locale={props.locale}
      back={{ to: props.backTo, label: text.back }}
    >
      {state.data && (
        <>
          <AppPropertyList title={state.data.document.title} items={props.properties} />
          {props.rows.length === 0 ? (
            <Text role="status">{text.noHistory}</Text>
          ) : (
            <AppResourceTable
              title={text.history}
              columns={[
                { id: "version", label: text.version, numeric: true },
                { id: "category", label: text.category },
                { id: "number", label: text.identityNumber },
                { id: "country", label: text.issuingCountry },
                { id: "issued", label: text.issuedOn },
                { id: "until", label: text.validUntil },
                { id: "actor", label: text.actor },
                { id: "recorded", label: text.recorded },
                { id: "reason", label: text.reason },
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
