import { Text } from "@fluentui/react-components";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppDetailsPanel } from "../../../../core/presentation/components/app-details-panel";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import { lifecycleCaseMessages } from "../i18n/lifecycle-case-messages";
import type { LifecycleHistoryState } from "../models/lifecycle-history-state";
import type { lifecycleEventView, lifecycleHistoryView } from "../models/lifecycle-history-view";

export function LifecycleHistoryPage({
  state,
  rows,
  selected,
  firstPage,
  locale,
  timezone,
  onRefresh,
  onFirst,
  onNext,
  onOpen,
  onClose,
}: {
  state: LifecycleHistoryState;
  rows: ReturnType<typeof lifecycleHistoryView>;
  selected: ReturnType<typeof lifecycleEventView> | null;
  firstPage: boolean;
  locale: Locale;
  timezone: string;
  onRefresh: () => void;
  onFirst: () => void;
  onNext: () => void;
  onOpen: (version: string) => void;
  onClose: () => void;
}) {
  const text = lifecycleCaseMessages(locale);
  return (
    <section className="app-report-content" aria-busy={state.stage === "loading"}>
      <AppFailure failure={state.failure} locale={locale} />
      {state.stage === "loading" && <AppLoading label={messages(locale).loading} />}
      {state.page &&
        (rows.length === 0 ? (
          <Text role="status">{text.historyEmpty}</Text>
        ) : (
          <AppResourceTable
            title={text.history}
            columns={[
              { id: "version", label: text.version, numeric: true },
              { id: "action", label: text.action },
              { id: "task", label: text.key },
              { id: "recorded", label: `${text.recordedAt} (${timezone})` },
            ]}
            rows={rows}
            action={{ label: text.viewEvent, onOpen }}
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
        <AppButton onClick={onRefresh} disabled={state.stage === "loading"}>
          {messages(locale).refresh}
        </AppButton>
      </fieldset>
      {selected && (
        <AppDetailsPanel open={true} title={text.event} closeLabel={text.close} onClose={onClose}>
          <AppPropertyList title={text.event} items={selected} />
        </AppDetailsPanel>
      )}
    </section>
  );
}
