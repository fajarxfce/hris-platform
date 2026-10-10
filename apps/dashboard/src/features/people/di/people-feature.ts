import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpEmployeeDataSource } from "../data/datasources/http-employee-data-source";
import { HttpEmployeeImportDataSource } from "../data/datasources/http-employee-import-data-source";
import { HttpPersonProfileDataSource } from "../data/datasources/http-person-profile-data-source";
import { RemoteEmployeeImportRepository } from "../data/repositories/remote-employee-import-repository";
import { RemoteEmployeeRepository } from "../data/repositories/remote-employee-repository";
import { RemotePersonProfileRepository } from "../data/repositories/remote-person-profile-repository";
import { CancelEmploymentRevision } from "../domain/usecases/cancel-employment-revision";
import { CreateEmployee } from "../domain/usecases/create-employee";
import { LoadEmployee } from "../domain/usecases/load-employee";
import { LoadEmployeeImport } from "../domain/usecases/load-employee-import";
import { LoadEmployeeImportAttempts } from "../domain/usecases/load-employee-import-attempts";
import { LoadEmployeeImportRows } from "../domain/usecases/load-employee-import-rows";
import { LoadEmployeeImports } from "../domain/usecases/load-employee-imports";
import { LoadEmployees } from "../domain/usecases/load-employees";
import { LoadEmploymentDetails } from "../domain/usecases/load-employment-details";
import { LoadEmploymentHistory } from "../domain/usecases/load-employment-history";
import { LoadEmploymentRevision } from "../domain/usecases/load-employment-revision";
import { LoadPersonProfile } from "../domain/usecases/load-person-profile";
import { LoadPersonProfileHistory } from "../domain/usecases/load-person-profile-history";
import { ReviseEmployment } from "../domain/usecases/revise-employment";
import { SavePersonProfile } from "../domain/usecases/save-person-profile";

export function createPeopleFeature(http: HttpClient) {
  const imports = new RemoteEmployeeImportRepository(new HttpEmployeeImportDataSource(http));
  const employees = new RemoteEmployeeRepository(new HttpEmployeeDataSource(http));
  const profiles = new RemotePersonProfileRepository(new HttpPersonProfileDataSource(http));
  return {
    loadEmployeeImports: new LoadEmployeeImports(imports),
    loadEmployeeImport: new LoadEmployeeImport(imports),
    loadEmployeeImportRows: new LoadEmployeeImportRows(imports),
    loadEmployeeImportAttempts: new LoadEmployeeImportAttempts(imports),
    loadEmploymentRevision: new LoadEmploymentRevision(employees),
    cancelEmploymentRevision: new CancelEmploymentRevision(employees),
    loadEmploymentDetails: new LoadEmploymentDetails(employees),
    reviseEmployment: new ReviseEmployment(employees),
    createEmployee: new CreateEmployee(employees),
    loadEmployees: new LoadEmployees(employees),
    loadEmployee: new LoadEmployee(employees),
    loadEmploymentHistory: new LoadEmploymentHistory(employees),
    loadPersonProfile: new LoadPersonProfile(profiles),
    loadPersonProfileHistory: new LoadPersonProfileHistory(profiles),
    savePersonProfile: new SavePersonProfile(profiles),
  };
}
