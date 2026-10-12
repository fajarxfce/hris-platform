import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { DocumentRetentionPolicy } from "../../domain/entities/document-retention-policy";
import { documentRetentionMessages } from "../i18n/document-retention-messages";
import type { DocumentReadState } from "../models/document-read-state";
import type { documentRetentionPolicyRows } from "../models/document-retention-view";
import { DocumentPageFrame } from "../widgets/document-page-frame";

export function DocumentRetentionPoliciesPage(props: {
  state: DocumentReadState<readonly DocumentRetentionPolicy[]>;
  rows: ReturnType<typeof documentRetentionPolicyRows>;
  locale: Locale;
  companyName: string;
  onOpen: (classification: string) => void;
}) {
  const text = documentRetentionMessages(props.locale);
  return (
    <DocumentPageFrame
      {...props.state}
      title={text.title}
      context={props.companyName}
      locale={props.locale}
    >
      {props.state.data && (
        <>
          <p>{text.policyHint}</p>
          <AppResourceTable
            title={text.policies}
            columns={[
              { id: "classification", label: text.classification },
              { id: "retention", label: text.retention },
              { id: "version", label: text.version, numeric: true },
            ]}
            rows={props.rows}
            action={{ label: text.view, onOpen: props.onOpen }}
          />
        </>
      )}
    </DocumentPageFrame>
  );
}
