import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { AudienceReferenceKind } from "../../domain/entities/audience-reference";
import type { CommunicationsScreenProps } from "../contracts/communications-screen-props";
import { useAudienceReferencePicker } from "../controllers/use-audience-reference-picker";
import { useSelectedAudienceReferences } from "../controllers/use-selected-audience-references";
import { announcementEditorMessages } from "../i18n/announcement-editor-messages";
import { AudienceSelectionPage } from "../pages/audience-selection-page";

export function AudienceSelection(
  props: Pick<CommunicationsScreenProps, "accountId" | "access" | "communications" | "locale"> & {
    kind: AudienceReferenceKind;
    ids: readonly string[];
    maximum: number;
  } & (
      | { readOnly: true; enabled?: never; onChange?: never }
      | { readOnly?: false; enabled: boolean; onChange: (ids: readonly string[]) => void }
    ),
) {
  const picker = useAudienceReferencePicker(
    props.accountId,
    props.access,
    props.kind,
    props.communications.loadReferences,
    props.readOnly !== true && props.enabled,
  );
  const selected = useSelectedAudienceReferences(
    props.accountId,
    props.access,
    props.kind,
    props.ids,
    props.communications.loadReferences,
  );
  useWorkspaceRevalidation(picker.failure ?? selected.failure);
  const text = announcementEditorMessages(props.locale);
  const references = new Map(selected.references.map((item) => [item.id, item]));
  const selectedRows = selected.ids.map((id) => {
    const item = references.get(id);
    return {
      id,
      actionLabel: item?.name ?? text.unavailable,
      cells: [
        item?.code ?? "—",
        item?.name ?? text.unavailable,
        item
          ? item.active === null
            ? text.existing
            : item.active
              ? text.active
              : text.inactive
          : "—",
      ],
    };
  });
  const selectedIds = new Set(props.ids);
  const available = picker.page?.items.filter((item) => !selectedIds.has(item.id)) ?? [];
  const availableRows = available.map((item) => ({
    id: item.id,
    actionLabel: item.name,
    cells: [item.code ?? "—", item.name],
  }));
  return (
    <AudienceSelectionPage
      picker={picker}
      selected={selected}
      selectedRows={selectedRows}
      availableRows={availableRows}
      count={props.ids.length}
      maximum={props.maximum}
      enabled={props.readOnly !== true && props.enabled}
      readOnly={props.readOnly === true}
      locale={props.locale}
      onAdd={(id) => {
        if (
          props.readOnly !== true &&
          props.enabled &&
          props.ids.length < props.maximum &&
          available.some((item) => item.id === id)
        )
          props.onChange([...props.ids, id]);
      }}
      onRemove={(id) => {
        if (props.readOnly !== true && props.enabled)
          props.onChange(props.ids.filter((item) => item !== id));
      }}
    />
  );
}
