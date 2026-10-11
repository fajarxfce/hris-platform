import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import { AppLoading } from "../../../../core/presentation/components/app-loading";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import { AppTextField } from "../../../../core/presentation/components/app-text-field";
import { type Locale, messages } from "../../../../core/presentation/i18n/messages";
import type { useAudienceReferencePicker } from "../controllers/use-audience-reference-picker";
import type { useSelectedAudienceReferences } from "../controllers/use-selected-audience-references";
import { announcementEditorMessages } from "../i18n/announcement-editor-messages";

type Row = Readonly<{ id: string; actionLabel: string; cells: readonly string[] }>;
export function AudienceSelectionPage(props: {
  picker: ReturnType<typeof useAudienceReferencePicker>;
  selected: ReturnType<typeof useSelectedAudienceReferences>;
  selectedRows: readonly Row[];
  availableRows: readonly Row[];
  count: number;
  maximum: number;
  enabled: boolean;
  readOnly: boolean;
  locale: Locale;
  onAdd: (id: string) => void;
  onRemove: (id: string) => void;
}) {
  const text = announcementEditorMessages(props.locale);
  return (
    <section className="app-report-content" aria-label={text.audience}>
      <div role="status">
        {text.selectedCount}: {props.count} · {text.limit}: {props.maximum}
      </div>
      <AppFailure failure={props.selected.failure} locale={props.locale} />
      {props.selected.failure && (
        <AppButton type="button" onClick={props.selected.refresh}>
          {text.reloadSelected}
        </AppButton>
      )}
      {props.selected.loading ? (
        <AppLoading label={messages(props.locale).loading} />
      ) : props.count > 0 ? (
        <AppResourceTable
          title={text.selected}
          columns={[
            { id: "code", label: text.code },
            { id: "name", label: text.name },
            { id: "status", label: text.status },
          ]}
          rows={props.selectedRows}
          {...(props.readOnly
            ? {}
            : { action: { label: text.remove, onOpen: props.onRemove, disabled: !props.enabled } })}
        />
      ) : (
        <p>{text.none}</p>
      )}
      {props.selected.lastPage > 0 && (
        <nav aria-label={text.selected}>
          <AppButton
            type="button"
            disabled={props.selected.loading || props.selected.page === 0}
            onClick={props.selected.previous}
          >
            {text.previous}
          </AppButton>
          <AppButton
            type="button"
            disabled={props.selected.loading || props.selected.page === props.selected.lastPage}
            onClick={props.selected.next}
          >
            {text.next}
          </AppButton>
        </nav>
      )}
      {props.enabled && (
        <>
          <search className="app-resource-filters" aria-label={text.available}>
            <AppTextField
              label={text.searchHint}
              {...props.picker.query}
              maxLength={120}
              onKeyDown={props.picker.onQueryKeyDown}
            />
            <AppButton type="button" disabled={props.picker.loading} onClick={props.picker.search}>
              {text.search}
            </AppButton>
            <AppButton type="button" disabled={props.picker.loading} onClick={props.picker.refresh}>
              {messages(props.locale).refresh}
            </AppButton>
          </search>
          <AppFailure failure={props.picker.failure} locale={props.locale} />
          {props.picker.loading ? (
            <AppLoading label={messages(props.locale).loading} />
          ) : props.availableRows.length > 0 ? (
            <AppResourceTable
              title={text.available}
              columns={[
                { id: "code", label: text.code },
                { id: "name", label: text.name },
              ]}
              rows={props.availableRows}
              action={{
                label: text.select,
                onOpen: props.onAdd,
                disabled: props.count >= props.maximum,
              }}
            />
          ) : (
            props.picker.page && (
              <p>{props.picker.page.items.length > 0 ? text.alreadySelected : text.empty}</p>
            )
          )}
          {(!props.picker.firstPage || props.picker.page?.nextCursor) && (
            <nav className="app-form-actions" aria-label={text.available}>
              <AppButton
                type="button"
                disabled={props.picker.loading || props.picker.firstPage}
                onClick={props.picker.first}
              >
                {text.first}
              </AppButton>
              <AppButton
                type="button"
                disabled={props.picker.loading || !props.picker.page?.nextCursor}
                onClick={props.picker.next}
              >
                {text.next}
              </AppButton>
            </nav>
          )}
        </>
      )}
    </section>
  );
}
