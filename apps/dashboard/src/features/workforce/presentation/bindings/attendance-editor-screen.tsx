import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import { useWorkspaceRevalidation } from "../../../../core/presentation/session/use-workspace-revalidation";
import type { WorkforceScreenProps } from "../contracts/workforce-screen-props";
import { AttendanceEditorController } from "../controllers/attendance-editor-controller";
import { useAttendanceCorrectionForm } from "../controllers/use-attendance-correction-form";
import { useAttendanceReviewForm } from "../controllers/use-attendance-review-form";
import type {
  AttendanceEditorMode,
  AttendanceEditorState,
} from "../models/attendance-editor-state";
import { attendanceDayPath, attendancePath } from "../models/attendance-route";
import { attendanceEntryView } from "../models/attendance-view";
import { calendarParameters, calendarRange } from "../models/calendar-route";
import { AttendanceCorrectionPage } from "../pages/attendance-correction-page";
import { AttendanceReviewPage } from "../pages/attendance-review-page";

export function AttendanceEditorScreen(
  props: WorkforceScreenProps & { kind: "review" | "correction" },
) {
  const { employeeId = "", workDate = "", entryId = "" } = useParams();
  const [parameters] = useSearchParams();
  const range = calendarRange(parameters, props.access.companyId, workDate);
  const pinned = calendarParameters(props.access.companyId, range).toString();
  const mode = useMemo<AttendanceEditorMode>(
    () =>
      props.kind === "review"
        ? { kind: "review", entryId: entryId.toLowerCase() }
        : { kind: "correction" },
    [props.kind, entryId],
  );
  if (parameters.has("company") && parameters.get("company") !== props.access.companyId)
    return <Navigate to={attendancePath} replace />;
  if (!parameters.has("company"))
    return (
      <Navigate
        to={
          attendanceDayPath(employeeId, workDate) +
          (props.kind === "review" ? `/review/${encodeURIComponent(entryId)}` : "/correction") +
          "?" +
          pinned
        }
        replace
      />
    );
  return (
    <AttendanceEditorBinding
      key={
        props.accountId +
        ":" +
        props.access.companyId +
        ":" +
        employeeId +
        ":" +
        workDate +
        ":" +
        props.kind +
        ":" +
        entryId
      }
      {...props}
      employeeId={employeeId}
      date={workDate}
      mode={mode}
      backTo={`${attendanceDayPath(employeeId, workDate)}?${pinned}`}
    />
  );
}
function AttendanceEditorBinding(
  props: WorkforceScreenProps & {
    employeeId: string;
    date: string;
    mode: AttendanceEditorMode;
    backTo: string;
  },
) {
  const controller = useMemo(
    () =>
      new AttendanceEditorController(
        props.workforce,
        props.access,
        props.employeeId,
        props.date,
        props.mode,
        props.nextIdentifier,
      ),
    [props.workforce, props.access, props.employeeId, props.date, props.mode, props.nextIdentifier],
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
  useWorkspaceRevalidation(state.failure);
  return props.mode.kind === "review" ? (
    <ReviewFormBinding {...props} controller={controller} state={state} />
  ) : (
    <CorrectionFormBinding {...props} controller={controller} state={state} />
  );
}
type FormProps = WorkforceScreenProps & {
  date: string;
  backTo: string;
  controller: AttendanceEditorController;
  state: AttendanceEditorState;
};
function ReviewFormBinding(props: FormProps) {
  const form = useAttendanceReviewForm(props.controller, props.state);
  const view = useMemo(
    () =>
      props.state.entry
        ? attendanceEntryView(props.state.entry, props.locale, props.timezone)
        : null,
    [props.state.entry, props.locale, props.timezone],
  );
  return (
    <AttendanceReviewPage
      state={props.state}
      form={form}
      view={view}
      locale={props.locale}
      companyName={props.companyName}
      backTo={props.backTo}
      onRetry={props.controller.retry}
    />
  );
}
function CorrectionFormBinding(props: FormProps) {
  const form = useAttendanceCorrectionForm(
    props.controller,
    props.state,
    props.timezone,
    props.locale,
  );
  return (
    <AttendanceCorrectionPage
      state={props.state}
      form={form}
      locale={props.locale}
      companyName={props.companyName}
      timezone={props.timezone}
      date={props.date}
      backTo={props.backTo}
      onRetry={props.controller.retry}
    />
  );
}
