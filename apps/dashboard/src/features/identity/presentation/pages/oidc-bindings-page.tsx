import { ArrowClockwise20Regular } from "@fluentui/react-icons";
import { Link } from "react-router-dom";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import { managedAccountMessages } from "../i18n/managed-account-messages";
import { oidcBindingMessages } from "../i18n/oidc-binding-messages";
import type { oidcBindingRows } from "../models/oidc-binding-view";
import type { OidcBindingsState } from "../models/oidc-bindings-state";

export function OidcBindingsPage({
  state,
  rows,
  locale,
  backTo,
  createTo,
  firstPage,
  onRefresh,
  onFirst,
  onNext,
  onOpen,
}: {
  state: OidcBindingsState;
  rows: ReturnType<typeof oidcBindingRows>;
  locale: Locale;
  backTo: string;
  createTo: string;
  firstPage: boolean;
  onRefresh: () => void;
  onFirst: () => void;
  onNext: () => void;
  onOpen: (id: string) => void;
}) {
  const text = oidcBindingMessages(locale);
  return (
    <section
      className="app-report-content app-platform-account app-oidc-binding"
      aria-busy={state.stage === "loading"}
    >
      <AppPageHeader
        title={text.title}
        context={state.directory?.account.displayName ?? managedAccountMessages(locale).details}
        actions={
          <>
            <Link to={backTo}>{managedAccountMessages(locale).backToAccount}</Link>
            {state.directory?.account.active && <Link to={createTo}>{text.create}</Link>}
            <AppButton
              appearance="subtle"
              icon={<ArrowClockwise20Regular />}
              aria-label={messages(locale).refresh}
              disabled={state.stage === "loading"}
              onClick={onRefresh}
            />
          </>
        }
      />
      <AppFailure failure={state.failure} locale={locale} />
      {state.stage === "loading" && <AppLoading label={messages(locale).loading} />}
      {state.directory && (
        <>
          <p>{state.directory.account.email}</p>
          {!state.directory.account.active && <p>{text.disabledAccount}</p>}
          {rows.length === 0 ? (
            <p role="status">{text.empty}</p>
          ) : (
            <AppResourceTable
              title={text.directory}
              rows={rows}
              columns={[
                { id: "subject", label: text.subject },
                { id: "issuer", label: text.issuer },
                { id: "status", label: text.status },
                { id: "created", label: text.linkedAt },
              ]}
              action={{ label: text.view, onOpen, columnId: "subject" }}
            />
          )}
        </>
      )}
      <fieldset className="app-pagination" aria-label={text.pages}>
        <AppButton disabled={firstPage || state.stage === "loading"} onClick={onFirst}>
          {text.first}
        </AppButton>
        <AppButton
          disabled={!state.directory?.page.nextCursor || state.stage === "loading"}
          onClick={onNext}
        >
          {text.next}
        </AppButton>
      </fieldset>
    </section>
  );
}
