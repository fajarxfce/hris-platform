import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { CommunicationsScreenProps } from "../contracts/communications-screen-props";
import { AnnouncementEditorController } from "../controllers/announcement-editor-controller";
import { useAnnouncementForm } from "../controllers/use-announcement-form";
import { announcementParameters, announcementsPath } from "../models/announcement-route";
import { AnnouncementEditorPage } from "../pages/announcement-editor-page";
import { AudienceSelection } from "./audience-selection";

export function AnnouncementEditorScreen(props: CommunicationsScreenProps & { creating: boolean }) {
  const { announcementId = "" } = useParams();
  const [parameters] = useSearchParams();
  const pinned = announcementParameters(props.access.companyId, parameters.get("after")).toString();
  const route = props.creating
    ? `${announcementsPath}/new`
    : `${announcementsPath}/${encodeURIComponent(announcementId)}/edit`;
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return (
      <Navigate
        to={`${announcementsPath}?${announcementParameters(props.access.companyId)}`}
        replace
      />
    );
  if (!parameters.has("company")) return <Navigate to={`${route}?${pinned}`} replace />;
  return (
    <AnnouncementEditorBinding
      key={`${props.accountId}:${props.access.companyId}:${route}`}
      {...props}
      id={announcementId}
      parameters={pinned}
    />
  );
}
function AnnouncementEditorBinding(
  props: CommunicationsScreenProps & { creating: boolean; id: string; parameters: string },
) {
  const id = useMemo(
    () => (props.creating ? props.nextIdentifier() : props.id),
    [props.creating, props.id, props.nextIdentifier],
  );
  const controller = useMemo(
    () =>
      new AnnouncementEditorController(
        props.communications,
        props.access,
        props.creating,
        id,
        props.nextIdentifier,
      ),
    [props.communications, props.access, props.creating, id, props.nextIdentifier],
  );
  const state = useSyncExternalStore(
    controller.subscribe,
    controller.getSnapshot,
    controller.getSnapshot,
  );
  useEffect(() => {
    controller.activate();
    return controller.deactivate;
  }, [controller]);
  const form = useAnnouncementForm(controller, state);
  useWorkspaceRevalidation(state.failure);
  return (
    <AnnouncementEditorPage
      state={state}
      form={form}
      creating={props.creating}
      companyName={props.companyName}
      locale={props.locale}
      backTo={`${announcementsPath}?${props.parameters}`}
      savedTo={
        state.receipt ? `${announcementsPath}/${state.receipt.id}?${props.parameters}` : null
      }
      onRetry={controller.retrySave}
      selection={
        form.audienceKind.value === "COMPANY" ? null : (
          <AudienceSelection
            key={form.audienceKind.value}
            accountId={props.accountId}
            access={props.access}
            communications={props.communications}
            locale={props.locale}
            kind={form.audienceKind.value}
            ids={form.targetIds.value}
            maximum={32}
            enabled={form.editable}
            onChange={form.targetIds.onChange}
          />
        )
      }
    />
  );
}
