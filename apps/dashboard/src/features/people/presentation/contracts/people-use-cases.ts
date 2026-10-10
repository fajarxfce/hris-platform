import type { LoadEmployee } from "../../domain/usecases/load-employee";
import type { LoadEmployees } from "../../domain/usecases/load-employees";
import type { LoadEmploymentHistory } from "../../domain/usecases/load-employment-history";

export type PeopleUseCases = Readonly<{
  loadEmployees: Pick<LoadEmployees, "execute">;
  loadEmployee: Pick<LoadEmployee, "execute">;
  loadEmploymentHistory: Pick<LoadEmploymentHistory, "execute">;
}>;
