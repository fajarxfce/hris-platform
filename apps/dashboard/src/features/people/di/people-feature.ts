import type { HttpClient } from "../../../core/data/http/http-client";
import { createBrowserFiles } from "../../../core/di/browser-files";
import type { FileRepository } from "../../../core/domain/files/file-repository";
import { HttpEmployeeDataSource } from "../data/datasources/http-employee-data-source";
import { HttpEmployeeImportDataSource } from "../data/datasources/http-employee-import-data-source";
import { HttpPersonProfileDataSource } from "../data/datasources/http-person-profile-data-source";
import { RemoteEmployeeImportRepository } from "../data/repositories/remote-employee-import-repository";
import { RemoteEmployeeRepository } from "../data/repositories/remote-employee-repository";
import { RemotePersonProfileRepository } from "../data/repositories/remote-person-profile-repository";
import { ApplyEmployeeImport } from "../domain/usecases/apply-employee-import";
import { BindPersonAccount } from "../domain/usecases/bind-person-account";
import { CancelEmployeeImport } from "../domain/usecases/cancel-employee-import";
import { CancelEmploymentRevision } from "../domain/usecases/cancel-employment-revision";
import { CreateEmployee } from "../domain/usecases/create-employee";
import { DownloadEmployeeImportTemplate } from "../domain/usecases/download-employee-import-template";
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
import { ResumeEmployeeImport } from "../domain/usecases/resume-employee-import";
import { ReviseEmployment } from "../domain/usecases/revise-employment";
import { SavePersonProfile } from "../domain/usecases/save-person-profile";
import { SelectEmployeeImportFile } from "../domain/usecases/select-employee-import-file";
import { StartEmployeeImport } from "../domain/usecases/start-employee-import";

export function createPeopleFeature(
  http: HttpClient,
  files: FileRepository = createBrowserFiles(),
) {
  const imports = new RemoteEmployeeImportRepository(new HttpEmployeeImportDataSource(http));
  const employees = new RemoteEmployeeRepository(new HttpEmployeeDataSource(http));
  const profiles = new RemotePersonProfileRepository(new HttpPersonProfileDataSource(http));
  return {
    bindPersonAccount: new BindPersonAccount(profiles),
    selectEmployeeImportFile: new SelectEmployeeImportFile(files),
    startEmployeeImport: new StartEmployeeImport(imports),
    downloadEmployeeImportTemplate: new DownloadEmployeeImportTemplate(imports, files),
    applyEmployeeImport: new ApplyEmployeeImport(imports),
    resumeEmployeeImport: new ResumeEmployeeImport(imports),
    cancelEmployeeImport: new CancelEmployeeImport(imports),
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
