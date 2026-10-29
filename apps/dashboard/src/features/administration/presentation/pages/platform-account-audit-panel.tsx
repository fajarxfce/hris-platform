import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import { administrationMessages } from "../i18n/administration-messages";
import type { PlatformAccountAuditState } from "../models/platform-account-audit-state";

export function PlatformAccountAuditPanel(props: {
  state: PlatformAccountAuditState;
  locale: Locale;
  onRetry: () => void;
}) {
  const text = administrationMessages(props.locale);
  const shared = messages(props.locale);
  const detail = props.state.review;
  return (
    <section aria-label={text.accountChange} aria-busy={props.state.stage === "loading"}>
      <p className="app-muted">{text.savedAccessHint}</p>
      {props.state.stage === "loading" && <AppLoading label={shared.loading} />}
      <AppFailure failure={props.state.failure} locale={props.locale} />
      {props.state.stage === "unavailable" && (
        <AppButton onClick={props.onRetry}>{shared.retry}</AppButton>
      )}
      {detail && (
        <AppPropertyList
          title={text.accountChange}
          items={[
            { label: text.account, value: detail.accountId },
            { label: text.savedStatus, value: detail.active ? text.active : text.inactive },
            {
              label: text.savedPermissions,
              value: detail.permissions.length ? detail.permissions.join(", ") : text.noPermissions,
            },
            { label: text.reason, value: detail.reason ?? text.noReason },
          ]}
        />
      )}
    </section>
  );
}
