import { Text } from "@fluentui/react-components";
import { Search20Regular } from "@fluentui/react-icons";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppCommandBar } from "../../../../core/presentation/components/app-command-bar";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { DocumentEmployeeDirectory } from "../../domain/entities/document-employee";
import type { useDocumentEmployeeFilter } from "../controllers/use-document-employee-filter";
import { documentMessages } from "../i18n/document-messages";
import type { DocumentReadState } from "../models/document-read-state";
import type { documentEmployeeRows } from "../models/document-view";
import { DocumentPageFrame } from "../widgets/document-page-frame";
import { DocumentPagination } from "../widgets/document-pagination";

export function DocumentEmployeesPage(props: {
  state: DocumentReadState<DocumentEmployeeDirectory>;
  filters: ReturnType<typeof useDocumentEmployeeFilter>;
  rows: ReturnType<typeof documentEmployeeRows>;
  locale: Locale;
  companyName: string;
  firstPage: boolean;
  onFirst: () => void;
  onNext: () => void;
  onOpen: (id: string) => void;
}) {
  const text = documentMessages(props.locale),
    { state, filters } = props;
  return (
    <DocumentPageFrame
      {...state}
      title={text.title}
      context={props.companyName}
      locale={props.locale}
    >
      <form onSubmit={filters.apply} noValidate>
        <AppCommandBar label={text.filters}>
          <AppTextField
            label={text.search}
            maxLength={120}
            contentBefore={<Search20Regular />}
            error={filters.invalid ? text.invalidSearch : undefined}
            {...filters.query}
          />
          <AppButton type="submit" disabled={state.loading}>
            {text.searchAction}
          </AppButton>
        </AppCommandBar>
      </form>
      {state.data &&
        (props.rows.length === 0 ? (
          <Text role="status">{text.emptyEmployees}</Text>
        ) : (
          <AppResourceTable
            title={text.employees}
            columns={[
              { id: "number", label: text.number },
              { id: "name", label: text.name },
              { id: "status", label: text.status },
            ]}
            rows={props.rows}
            action={{ label: text.view, onOpen: props.onOpen }}
          />
        ))}
      <DocumentPagination
        {...props}
        loading={state.loading}
        hasNext={state.data?.employees.nextCursor != null}
      />
    </DocumentPageFrame>
  );
}
