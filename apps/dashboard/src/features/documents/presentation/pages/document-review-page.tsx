import { Text } from "@fluentui/react-components";
import { Link } from "react-router-dom";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { DocumentReview } from "../../domain/entities/document-review";
import { documentMessages } from "../i18n/document-messages";
import type { DocumentReadState } from "../models/document-read-state";
import type { documentProperties, documentRevisionRows } from "../models/document-view";
import { DocumentPageFrame } from "../widgets/document-page-frame";
import { DocumentPagination } from "../widgets/document-pagination";

export function DocumentReviewPage(props: {
  state: DocumentReadState<DocumentReview>;
  properties: ReturnType<typeof documentProperties>;
  rows: ReturnType<typeof documentRevisionRows>;
  locale: Locale;
  companyName: string;
  backTo: string;
  publishedTo: string | null;
  firstPage: boolean;
  onFirst: () => void;
  onNext: () => void;
  onOpen: (id: string) => void;
}) {
  const text = documentMessages(props.locale),
    { state } = props;
  return (
    <DocumentPageFrame
      {...state}
      title={state.data?.document.title ?? text.details}
      context={`${props.companyName} / ${text.title}`}
      locale={props.locale}
      back={{ to: props.backTo, label: text.backFolder }}
    >
      {state.data && (
        <>
          <AppPropertyList
            title={text.overview}
            items={[
              ...props.properties,
              {
                label: text.published,
                value: props.publishedTo ? (
                  <Link to={props.publishedTo}>{text.openPublished}</Link>
                ) : (
                  text.unpublished
                ),
              },
            ]}
          />
          {props.rows.length === 0 ? (
            <Text role="status">{text.noRevisions}</Text>
          ) : (
            <AppResourceTable
              title={text.revisions}
              columns={[
                { id: "number", label: text.revision, numeric: true },
                { id: "file", label: text.fileName },
                { id: "status", label: text.status },
                { id: "size", label: text.size, numeric: true },
                { id: "created", label: text.created },
              ]}
              rows={props.rows}
              action={{ label: text.viewRevision, onOpen: props.onOpen }}
            />
          )}
        </>
      )}
      <DocumentPagination
        {...props}
        loading={state.loading}
        hasNext={state.data?.revisions.nextCursor != null}
      />
    </DocumentPageFrame>
  );
}
