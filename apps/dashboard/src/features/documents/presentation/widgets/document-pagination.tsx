import { AppButton } from "../../../../core/presentation/components/app-button";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { documentMessages } from "../i18n/document-messages";

export function DocumentPagination(props: {
  locale: Locale;
  firstPage: boolean;
  hasNext: boolean;
  loading: boolean;
  onFirst: () => void;
  onNext: () => void;
}) {
  const text = documentMessages(props.locale);
  return (
    <fieldset className="app-pagination" aria-label={text.pagination}>
      <AppButton
        appearance="subtle"
        disabled={props.firstPage || props.loading}
        onClick={props.onFirst}
      >
        {text.first}
      </AppButton>
      <AppButton
        appearance="subtle"
        disabled={!props.hasNext || props.loading}
        onClick={props.onNext}
      >
        {text.next}
      </AppButton>
    </fieldset>
  );
}
