import { ArrowClockwise20Regular } from "@fluentui/react-icons";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import { roleTemplateMessages } from "../i18n/role-template-messages";
import type { roleTemplateRows } from "../models/role-template-view";
import type { RoleTemplatesState } from "../models/role-templates-state";
export function RoleTemplatesPage({
  state,
  rows,
  companyName,
  locale,
  firstPage,
  createTo,
  onRefresh,
  onFirst,
  onNext,
  onOpen,
}: {
  state: RoleTemplatesState;
  rows: ReturnType<typeof roleTemplateRows>;
  companyName: string;
  locale: Locale;
  firstPage: boolean;
  createTo: string | null;
  onRefresh: () => void;
  onFirst: () => void;
  onNext: () => void;
  onOpen: (id: string) => void;
}) {
  const text = roleTemplateMessages(locale);
  return (
    <section aria-busy={state.stage === "loading"}>
      <AppPageHeader
        title={text.title}
        context={companyName}
        actions={
          <>
            {createTo && <Link to={createTo}>{text.create}</Link>}
            <AppButton
              appearance="subtle"
              icon={<ArrowClockwise20Regular />}
              disabled={state.stage === "loading"}
              onClick={onRefresh}
            >
              {messages(locale).refresh}
            </AppButton>
          </>
        }
      />
      <AppFailure failure={state.failure} locale={locale} />
      <div className="app-report-content">
        {state.stage === "loading" && <AppLoading label={messages(locale).loading} />}
        {state.page &&
          (rows.length === 0 ? (
            <p role="status">{text.empty}</p>
          ) : (
            <AppResourceTable
              title={text.directory}
              columns={[
                { id: "name", label: text.name },
                { id: "code", label: text.code },
                { id: "status", label: text.status },
                { id: "permissions", label: text.permissions, numeric: true },
                { id: "version", label: text.version, numeric: true },
              ]}
              rows={rows}
              action={{ label: text.view, onOpen, columnId: "name" }}
            />
          ))}
        <fieldset className="app-pagination" aria-label={text.pages}>
          <AppButton onClick={onFirst} disabled={firstPage || state.stage === "loading"}>
            {text.first}
          </AppButton>
          <AppButton
            onClick={onNext}
            disabled={state.page?.nextCursor == null || state.stage === "loading"}
          >
            {text.next}
          </AppButton>
        </fieldset>
      </div>
    </section>
  );
}
