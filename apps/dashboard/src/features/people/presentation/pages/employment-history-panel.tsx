import { Text } from "@fluentui/react-components";
import { ArrowClockwise20Regular } from "@fluentui/react-icons";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppDetailsPanel } from "../../../../core/presentation/components/app-details-panel";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import { peopleMessages } from "../i18n/people-messages";
import type { EmploymentHistoryState } from "../models/employment-history-state";
import type {
  employmentHistoryView,
  employmentRevisionView,
} from "../models/employment-history-view";

export function EmploymentHistoryPanel({
  state,
  rows,
  revision,
  locale,
  firstPage,
  onRefresh,
  onFirst,
  onNext,
  onOpen,
  onClose,
}: {
  state: EmploymentHistoryState;
  rows: ReturnType<typeof employmentHistoryView>;
  revision: ReturnType<typeof employmentRevisionView> | null;
  locale: Locale;
  firstPage: boolean;
  onRefresh: () => void;
  onFirst: () => void;
  onNext: () => void;
  onOpen: (revision: string) => void;
  onClose: () => void;
}) {
  const text = peopleMessages(locale);
  const shared = messages(locale);
  return (
    <section className="app-report-content" aria-busy={state.stage === "loading"}>
      <div className="app-filter-actions">
        <Text className="app-muted">{text.historyNote}</Text>
        <AppButton
          icon={<ArrowClockwise20Regular />}
          disabled={state.stage === "loading"}
          onClick={onRefresh}
        >
          {shared.refresh}
        </AppButton>
      </div>
      <AppFailure failure={state.failure} locale={locale} />
      {state.stage === "loading" && <AppLoading label={shared.loading} />}
      {state.page &&
        (rows.length === 0 ? (
          <Text role="status">{text.emptyHistory}</Text>
        ) : (
          <AppResourceTable
            title={text.history}
            columns={[
              { id: "revision", label: text.revision, numeric: true },
              { id: "effective", label: text.effectiveFrom },
              { id: "status", label: text.status },
              { id: "disposition", label: text.disposition },
            ]}
            rows={rows}
            action={{ label: text.view, onOpen }}
          />
        ))}
      <fieldset className="app-pagination" aria-label={text.historyPages}>
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
      <AppDetailsPanel
        open={revision !== null}
        title={text.revisionDetails}
        closeLabel={shared.close}
        onClose={onClose}
      >
        {revision && <AppPropertyList title={text.revision} items={revision} />}
      </AppDetailsPanel>
    </section>
  );
}
