import { Text } from "@fluentui/react-components";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { DocumentFolder } from "../../domain/entities/document-folder";
import { documentMessages } from "../i18n/document-messages";
import type { DocumentReadState } from "../models/document-read-state";
import type { documentRows } from "../models/document-view";
import { DocumentPageFrame } from "../widgets/document-page-frame";
import { DocumentPagination } from "../widgets/document-pagination";

export function DocumentFolderPage(props: {
  state: DocumentReadState<DocumentFolder>;
  rows: ReturnType<typeof documentRows>;
  locale: Locale;
  companyName: string;
  backTo: string;
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
      title={state.data?.employee.name ?? text.folder}
      context={`${props.companyName} / ${text.title}`}
      locale={props.locale}
      back={{ to: props.backTo, label: text.back }}
    >
      {state.data && (
        <>
          <Text>
            {text.number}: {state.data.employee.employeeNumber}
          </Text>
          {props.rows.length === 0 ? (
            <Text role="status">{text.emptyDocuments}</Text>
          ) : (
            <AppResourceTable
              title={text.title}
              columns={[
                { id: "title", label: text.documentTitle },
                { id: "classification", label: text.classification },
                { id: "revisions", label: text.revisions, numeric: true },
                { id: "created", label: text.created },
              ]}
              rows={props.rows}
              action={{ label: text.viewDocument, onOpen: props.onOpen }}
            />
          )}
        </>
      )}
      <DocumentPagination
        {...props}
        loading={state.loading}
        hasNext={state.data?.documents.nextCursor != null}
      />
    </DocumentPageFrame>
  );
}
