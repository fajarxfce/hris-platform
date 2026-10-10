import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpEmployeeDataSource } from "../data/datasources/http-employee-data-source";
import { HttpPersonProfileDataSource } from "../data/datasources/http-person-profile-data-source";
import { RemoteEmployeeRepository } from "../data/repositories/remote-employee-repository";
import { RemotePersonProfileRepository } from "../data/repositories/remote-person-profile-repository";
import { CreateEmployee } from "../domain/usecases/create-employee";
import { LoadEmployee } from "../domain/usecases/load-employee";
import { LoadEmployees } from "../domain/usecases/load-employees";
import { LoadEmploymentDetails } from "../domain/usecases/load-employment-details";
import { LoadEmploymentHistory } from "../domain/usecases/load-employment-history";
import { LoadPersonProfile } from "../domain/usecases/load-person-profile";
import { LoadPersonProfileHistory } from "../domain/usecases/load-person-profile-history";
import { ReviseEmployment } from "../domain/usecases/revise-employment";
import { SavePersonProfile } from "../domain/usecases/save-person-profile";

export function createPeopleFeature(http: HttpClient) {
  const employees = new RemoteEmployeeRepository(new HttpEmployeeDataSource(http));
  const profiles = new RemotePersonProfileRepository(new HttpPersonProfileDataSource(http));
  return {
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
