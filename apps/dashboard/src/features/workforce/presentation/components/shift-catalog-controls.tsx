import { Text } from "@fluentui/react-components";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppCommandBar } from "../../../../core/presentation/components/app-command-bar";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { useShiftCatalog } from "../controllers/use-shift-catalog";
import { calendarMessages } from "../i18n/calendar-messages";

export function ShiftCatalogControls({
  catalog,
  locale,
  disabled,
}: {
  catalog: ReturnType<typeof useShiftCatalog>;
  locale: Locale;
  disabled: boolean;
}) {
  const text = calendarMessages(locale);
  return (
    <form onSubmit={catalog.apply} noValidate>
      <AppCommandBar label={text.searchShifts}>
        <div className="app-form-actions">
          <AppTextField
            label={text.searchShifts}
            maxLength={120}
            {...catalog.query}
            disabled={disabled}
          />
          <AppButton type="submit" disabled={disabled}>
            {text.apply}
          </AppButton>
        </div>
        <AppFailure failure={catalog.failure} locale={locale} />
        {catalog.loading && <AppLoading label={messages(locale).loading} />}
        {!catalog.loading && !catalog.failure && !catalog.hasActiveShifts && (
          <Text>{text.noShifts}</Text>
        )}
        <fieldset className="app-pagination" aria-label={text.shiftPage}>
          <AppButton
            disabled={disabled || catalog.loading || catalog.firstPage}
            onClick={catalog.first}
          >
            {text.first}
          </AppButton>
          <AppButton
            disabled={disabled || catalog.loading || catalog.nextCursor === null}
            onClick={catalog.next}
          >
            {text.next}
          </AppButton>
        </fieldset>
      </AppCommandBar>
    </form>
  );
}
