import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { DocumentRetentionPolicy } from "../../domain/entities/document-retention-policy";
import type { useDocumentRetentionLookup } from "../controllers/use-document-retention-lookup";
import { documentRetentionLifecycleMessages } from "../i18n/document-retention-lifecycle-messages";
import { documentRetentionMessages } from "../i18n/document-retention-messages";
import type { DocumentReadState } from "../models/document-read-state";
import type { documentRetentionPolicyRows } from "../models/document-retention-view";
import { DocumentPageFrame } from "../widgets/document-page-frame";

export function DocumentRetentionPoliciesPage(props: {
  state: DocumentReadState<readonly DocumentRetentionPolicy[]>;
  lookup: ReturnType<typeof useDocumentRetentionLookup>;
  rows: ReturnType<typeof documentRetentionPolicyRows>;
  locale: Locale;
  companyName: string;
  onOpen: (classification: string) => void;
}) {
  const text = documentRetentionMessages(props.locale);
  const lifecycle = documentRetentionLifecycleMessages(props.locale);
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
          <section className="app-resource-panel" aria-label={lifecycle.lookup}>
            <h2>{lifecycle.lookup}</h2>
            <form className="app-resource-filters" onSubmit={props.lookup.submit} noValidate>
              <AppTextField
                label={lifecycle.reference}
                hint={lifecycle.referenceHint}
                {...props.lookup.documentId}
                maxLength={36}
                error={props.lookup.invalid ? lifecycle.invalidReference : undefined}
              />
              <AppButton type="submit">{lifecycle.open}</AppButton>
            </form>
          </section>
        </>
      )}
    </DocumentPageFrame>
  );
}
