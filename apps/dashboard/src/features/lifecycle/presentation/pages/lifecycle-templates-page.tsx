import { Text } from "@fluentui/react-components";
import { ArrowClockwise20Regular } from "@fluentui/react-icons";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import { lifecycleMessages } from "../i18n/lifecycle-messages";
import type { lifecycleTemplatesView } from "../models/lifecycle-template-view";
import type { LifecycleTemplatesState } from "../models/lifecycle-templates-state";

export function LifecycleTemplatesPage({
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
  state: LifecycleTemplatesState;
  rows: ReturnType<typeof lifecycleTemplatesView>;
  companyName: string;
  locale: Locale;
  firstPage: boolean;
  createTo: string | null;
  onRefresh: () => void;
  onFirst: () => void;
  onNext: () => void;
  onOpen: (id: string) => void;
}) {
  const text = lifecycleMessages(locale);
  const shared = messages(locale);
  return (
    <section aria-busy={state.stage === "loading"}>
      <AppPageHeader
        title={text.title}
        context={companyName}
        actions={
          <>
            {createTo && <Link to={createTo}>{text.create}</Link>}
            <AppButton
              icon={<ArrowClockwise20Regular />}
              disabled={state.stage === "loading"}
              onClick={onRefresh}
            >
              {shared.refresh}
            </AppButton>
          </>
        }
      />
      <AppFailure failure={state.failure} locale={locale} />
      {state.stage === "loading" && <AppLoading label={shared.loading} />}
      <div className="app-report-content">
        {state.page &&
          (rows.length === 0 ? (
            <Text role="status">{text.empty}</Text>
          ) : (
            <AppResourceTable
              title={text.directory}
              columns={[
                { id: "code", label: text.code },
                { id: "name", label: text.name },
                { id: "kind", label: text.kind },
                { id: "status", label: text.status },
                { id: "tasks", label: text.taskCount, numeric: true },
              ]}
              rows={rows}
              action={{ label: text.view, onOpen }}
            />
          ))}
        <fieldset className="app-pagination" aria-label={text.page}>
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
