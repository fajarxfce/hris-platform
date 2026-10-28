import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import { workerIntegrationsMessages } from "../i18n/worker-integrations-messages";
import type { WorkerIntegrationsState } from "../models/worker-integrations-state";
import type { workerIntegrationsView } from "../models/worker-integrations-view";

export function WorkerIntegrationsPanel({
  state,
  view,
  locale,
}: {
  state: WorkerIntegrationsState;
  view: ReturnType<typeof workerIntegrationsView> | null;
  locale: Locale;
}) {
  const text = workerIntegrationsMessages(locale);
  return (
    <section
      className="app-report-content"
      aria-busy={state.stage === "loading"}
      aria-label={text.title}
    >
      <h2>{text.title}</h2>
      <p>{text.summary}</p>
      <AppFailure failure={state.failure} locale={locale} />
      {state.stage === "loading" && <AppLoading label={messages(locale).loading} />}
      {view && (
        <>
          <p>
            {text.observedAt}: {view.observedAt}
          </p>
          {view.rows.length === 0 ? (
            <p>{text.empty}</p>
          ) : (
            <AppResourceTable
              title={text.title}
              columns={view.columns}
              rows={view.rows}
              minWidth={1000}
            />
          )}
        </>
      )}
    </section>
  );
}
