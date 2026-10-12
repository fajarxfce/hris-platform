import { ArrowClockwise20Regular } from "@fluentui/react-icons";
import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import type { Failure } from "../../../../core/domain/result";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppPageHeader } from "../../../../core/presentation/components/app-page-header";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";

export function DocumentPageFrame(props: {
  title: string;
  context: string;
  locale: Locale;
  loading: boolean;
  failure: Failure | null;
  refresh: () => void;
  back?: Readonly<{ to: string; label: string }>;
  children?: ReactNode;
}) {
  return (
    <section aria-busy={props.loading}>
      <AppPageHeader
        title={props.title}
        context={props.context}
        actions={
          <>
            {props.back && <Link to={props.back.to}>{props.back.label}</Link>}
            <AppButton
              appearance="subtle"
              icon={<ArrowClockwise20Regular />}
              disabled={props.loading}
              onClick={props.refresh}
            >
              {messages(props.locale).refresh}
            </AppButton>
          </>
        }
      />
      <AppFailure failure={props.failure} locale={props.locale} />
      {props.loading && <AppLoading label={messages(props.locale).loading} />}
      <div className="app-report-content">{props.children}</div>
    </section>
  );
}
