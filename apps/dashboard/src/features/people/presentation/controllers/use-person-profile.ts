import { useEffect, useMemo, useSyncExternalStore } from "react";
import type { CompanyAccess } from "../../../identity/domain/entities/session";
import type { PeopleUseCases } from "../contracts/people-use-cases";
import { PersonProfileController } from "./person-profile-controller";

export function usePersonProfile(
  actions: PeopleUseCases,
  access: CompanyAccess,
  employeeId: string,
  mode: "read" | "edit",
  nextIdentifier: () => string,
) {
  const controller = useMemo(
    () => new PersonProfileController(actions, access, employeeId, mode, nextIdentifier),
    [actions, access, employeeId, mode, nextIdentifier],
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
  return { controller, state };
}
