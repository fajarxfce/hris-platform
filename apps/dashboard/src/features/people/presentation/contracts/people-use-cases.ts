import type { CancelEmploymentRevision } from "../../domain/usecases/cancel-employment-revision";
import type { CreateEmployee } from "../../domain/usecases/create-employee";
import type { LoadEmployee } from "../../domain/usecases/load-employee";
import type { LoadEmployees } from "../../domain/usecases/load-employees";
import type { LoadEmploymentDetails } from "../../domain/usecases/load-employment-details";
import type { LoadEmploymentHistory } from "../../domain/usecases/load-employment-history";
import type { LoadEmploymentRevision } from "../../domain/usecases/load-employment-revision";
import type { LoadPersonProfile } from "../../domain/usecases/load-person-profile";
import type { LoadPersonProfileHistory } from "../../domain/usecases/load-person-profile-history";
import type { ReviseEmployment } from "../../domain/usecases/revise-employment";
import type { SavePersonProfile } from "../../domain/usecases/save-person-profile";

export type PeopleUseCases = Readonly<{
  loadEmploymentRevision: Pick<LoadEmploymentRevision, "execute">;
  cancelEmploymentRevision: Pick<CancelEmploymentRevision, "execute">;
  loadEmploymentDetails: Pick<LoadEmploymentDetails, "execute">;
  reviseEmployment: Pick<ReviseEmployment, "execute">;
  createEmployee: Pick<CreateEmployee, "execute">;
  loadEmployees: Pick<LoadEmployees, "execute">;
  loadEmployee: Pick<LoadEmployee, "execute">;
  loadEmploymentHistory: Pick<LoadEmploymentHistory, "execute">;
  loadPersonProfile: Pick<LoadPersonProfile, "execute">;
  loadPersonProfileHistory: Pick<LoadPersonProfileHistory, "execute">;
  savePersonProfile: Pick<SavePersonProfile, "execute">;
}>;
